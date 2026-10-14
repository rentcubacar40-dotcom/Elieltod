package cu.todus.download.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import cu.todus.download.data.AppPreferences
import cu.todus.download.data.DownloadItem
import cu.todus.download.data.DownloadRepository
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class DownloadEngine(
    private val context: Context,
    private val preferences: AppPreferences,
    private val repository: DownloadRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val networkClient = TodusNetworkClient(preferences)

    fun startDownload(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) return

        val job = scope.launch {
            runDownload(item)
        }
        activeJobs[item.id] = job
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val current = repository.downloads.value.find { it.id == id }
        if (current != null && current.status != DownloadItem.Status.COMPLETED) {
            repository.updateItem(
                current.copy(
                    status = DownloadItem.Status.PAUSED,
                    speedBps = 0L
                )
            )
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val current = repository.downloads.value.find { it.id == id }
        if (current != null) {
            // Delete incomplete temporary files if any
            if (current.destinationPath.isNotBlank()) {
                val partFile = File("${current.destinationPath}.tdpart")
                if (partFile.exists()) partFile.delete()
            }
            repository.updateItem(
                current.copy(
                    status = DownloadItem.Status.CANCELLED,
                    speedBps = 0L
                )
            )
        }
    }

    private fun getDestinationFolder(): File {
        val externalDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(externalDownloads, preferences.subFolder)
        if (!target.exists()) {
            target.mkdirs()
        }
        if (target.canWrite()) return target

        // Fallback to app external files or internal files
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appExternal != null) {
            val appTarget = File(appExternal, preferences.subFolder)
            if (!appTarget.exists()) appTarget.mkdirs()
            return appTarget
        }

        val internalTarget = File(context.filesDir, preferences.subFolder)
        if (!internalTarget.exists()) internalTarget.mkdirs()
        return internalTarget
    }

    private suspend fun runDownload(initialItem: DownloadItem) {
        var currentItem = initialItem.copy(
            status = DownloadItem.Status.CONNECTING,
            errorMsg = null,
            speedBps = 0L
        )
        repository.updateItem(currentItem)

        try {
            // Step 1: Probe metadata if unknown
            var totalBytes = currentItem.totalBytes
            var cleanName = currentItem.name

            val metadata = withContext(Dispatchers.IO) {
                try {
                    networkClient.probeFileInfo(currentItem.url)
                } catch (_: Exception) {
                    null
                }
            }

            if (metadata != null) {
                if (totalBytes <= 0L && metadata.contentLength > 0L) {
                    totalBytes = metadata.contentLength
                }
                if (cleanName.isBlank() || cleanName.startsWith("todus_file_") || cleanName == "archivo.bin") {
                    cleanName = metadata.fileName
                }
            }

            if (cleanName.isBlank()) {
                cleanName = "todus_${System.currentTimeMillis()}.bin"
            }

            val targetDir = getDestinationFolder()
            val finalFile = File(targetDir, cleanName)
            val partFile = File(targetDir, "$cleanName.tdpart")

            currentItem = currentItem.copy(
                name = cleanName,
                totalBytes = totalBytes,
                destinationPath = finalFile.absolutePath,
                status = DownloadItem.Status.DOWNLOADING
            )
            repository.updateItem(currentItem)

            val client = networkClient.getClient()
            val threadsCount = currentItem.threads.coerceIn(1, 16)
            val downloadedBytesCounter = AtomicLong(partFile.length())

            // If we have total length and multiple threads requested and ranges supported
            if (totalBytes > 1024 * 1024 && threadsCount > 1 && (metadata?.supportsRanges != false)) {
                runMultiThreadDownload(
                    item = currentItem,
                    totalBytes = totalBytes,
                    partFile = partFile,
                    threadsCount = threadsCount,
                    downloadedBytesCounter = downloadedBytesCounter
                )
            } else {
                runSingleThreadDownload(
                    item = currentItem,
                    partFile = partFile,
                    totalBytes = totalBytes,
                    downloadedBytesCounter = downloadedBytesCounter
                )
            }

            // Finished download: rename partFile to finalFile
            if (partFile.exists()) {
                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)
            }

            // Index with Android MediaScanner
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}

            val finalLength = if (finalFile.exists()) finalFile.length() else totalBytes
            val completedItem = currentItem.copy(
                status = DownloadItem.Status.COMPLETED,
                downloadedBytes = finalLength,
                totalBytes = if (totalBytes > 0) totalBytes else finalLength,
                speedBps = 0L,
                destinationPath = finalFile.absolutePath,
                completedTimestamp = System.currentTimeMillis()
            )
            repository.updateItem(completedItem)

        } catch (ce: CancellationException) {
            // Cancelled or paused
        } catch (e: Exception) {
            e.printStackTrace()
            val errorItem = currentItem.copy(
                status = DownloadItem.Status.ERROR,
                errorMsg = e.localizedMessage ?: "Error de red desconocido",
                speedBps = 0L
            )
            repository.updateItem(errorItem)
        } finally {
            activeJobs.remove(initialItem.id)
        }
    }

    private suspend fun runSingleThreadDownload(
        item: DownloadItem,
        partFile: File,
        totalBytes: Long,
        downloadedBytesCounter: AtomicLong
    ) = coroutineScope {
        val existingBytes = partFile.length()
        val client = networkClient.getClient()

        val reqBuilder = Request.Builder().url(item.url)
        if (existingBytes > 0 && totalBytes > existingBytes) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = reqBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            throw java.io.IOException("Código de error HTTP: ${response.code}")
        }

        val body = response.body ?: throw java.io.IOException("Cuerpo de respuesta vacío")

        val append = existingBytes > 0 && response.code == 206
        var writtenSoFar = if (append) existingBytes else 0L

        // Speed meter job
        var lastBytes = writtenSoFar
        val speedJob = launch {
            while (isActive) {
                delay(800)
                val current = writtenSoFar
                val delta = (current - lastBytes).coerceAtLeast(0)
                lastBytes = current
                val speed = (delta * 1000) / 800

                repository.updateItem(
                    item.copy(
                        downloadedBytes = current,
                        totalBytes = if (totalBytes > 0) totalBytes else current,
                        speedBps = speed,
                        status = DownloadItem.Status.DOWNLOADING
                    )
                )
            }
        }

        try {
            body.byteStream().use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, read)
                        writtenSoFar += read
                        downloadedBytesCounter.set(writtenSoFar)
                    }
                    output.flush()
                }
            }
        } finally {
            speedJob.cancel()
        }
    }

    private suspend fun runMultiThreadDownload(
        item: DownloadItem,
        totalBytes: Long,
        partFile: File,
        threadsCount: Int,
        downloadedBytesCounter: AtomicLong
    ) = coroutineScope {
        // Pre-allocate file size
        RandomAccessFile(partFile, "rw").use { raf ->
            if (raf.length() < totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        val chunkSize = totalBytes / threadsCount
        val client = networkClient.getClient()

        // Speed tracking
        var lastBytes = downloadedBytesCounter.get()
        val speedJob = launch {
            while (isActive) {
                delay(800)
                val current = downloadedBytesCounter.get()
                val delta = (current - lastBytes).coerceAtLeast(0)
                lastBytes = current
                val speed = (delta * 1000) / 800

                repository.updateItem(
                    item.copy(
                        downloadedBytes = current,
                        totalBytes = totalBytes,
                        speedBps = speed,
                        status = DownloadItem.Status.DOWNLOADING
                    )
                )
            }
        }

        try {
            val chunkJobs = (0 until threadsCount).map { i ->
                val startByte = i * chunkSize
                val endByte = if (i == threadsCount - 1) totalBytes - 1 else (startByte + chunkSize - 1)

                launch(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url(item.url)
                        .header("Range", "bytes=$startByte-$endByte")
                        .build()

                    val resp = client.newCall(req).execute()
                    if (!resp.isSuccessful && resp.code != 206) {
                        throw java.io.IOException("Error en hilo $i (HTTP ${resp.code})")
                    }

                    val body = resp.body ?: throw java.io.IOException("Cuerpo vacío en hilo $i")
                    body.byteStream().use { input ->
                        RandomAccessFile(partFile, "rw").use { raf ->
                            raf.seek(startByte)
                            val buffer = ByteArray(32 * 1024)
                            var bytesLeft = endByte - startByte + 1
                            while (bytesLeft > 0) {
                                ensureActive()
                                val toRead = min(buffer.size.toLong(), bytesLeft).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break
                                raf.write(buffer, 0, read)
                                bytesLeft -= read
                                downloadedBytesCounter.addAndGet(read.toLong())
                            }
                        }
                    }
                }
            }

            chunkJobs.joinAll()
        } finally {
            speedJob.cancel()
        }
    }
}
