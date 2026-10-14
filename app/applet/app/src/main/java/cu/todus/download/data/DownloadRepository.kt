package cu.todus.download.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val file = File(context.filesDir, "downloads_cache.json")

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!file.exists()) {
            // Initial sample/example item from toDus to showcase UI
            val sample = listOf(
                DownloadItem(
                    id = "sample_1",
                    url = "https://s3.todus.cu/stream/users/tg_7363341763/telegram/Mushoku%20Tensei.mp4",
                    name = "Mushoku Tensei 3-4.mp4",
                    totalBytes = 350 * 1024 * 1024L,
                    downloadedBytes = 145 * 1024 * 1024L,
                    speedBps = (3.2 * 1024 * 1024).toLong(),
                    status = DownloadItem.Status.PAUSED,
                    threads = 4,
                    destinationPath = ""
                )
            )
            _downloads.value = sample
            saveToDisk(sample)
            return
        }

        try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", "QUEUED")
                val status = try {
                    DownloadItem.Status.valueOf(statusStr)
                } catch (_: Exception) {
                    DownloadItem.Status.QUEUED
                }
                list.add(
                    DownloadItem(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        name = obj.getString("name"),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        speedBps = 0L,
                        status = if (status == DownloadItem.Status.DOWNLOADING || status == DownloadItem.Status.CONNECTING) DownloadItem.Status.PAUSED else status,
                        threads = obj.optInt("threads", 4),
                        destinationPath = obj.optString("destinationPath", ""),
                        errorMsg = if (obj.has("errorMsg")) obj.getString("errorMsg") else null,
                        addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis()),
                        completedTimestamp = obj.optLong("completedTimestamp", 0L),
                        mimeType = obj.optString("mimeType", "")
                    )
                )
            }
            _downloads.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToDisk(list: List<DownloadItem>) {
        scope.launch {
            try {
                val array = JSONArray()
                for (item in list) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("url", item.url)
                        put("name", item.name)
                        put("totalBytes", item.totalBytes)
                        put("downloadedBytes", item.downloadedBytes)
                        put("status", item.status.name)
                        put("threads", item.threads)
                        put("destinationPath", item.destinationPath)
                        item.errorMsg?.let { put("errorMsg", it) }
                        put("addedTimestamp", item.addedTimestamp)
                        put("completedTimestamp", item.completedTimestamp)
                        put("mimeType", item.mimeType)
                    }
                    array.put(obj)
                }
                file.writeText(array.toString(2))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun addItem(item: DownloadItem) {
        val current = _downloads.value.toMutableList()
        current.add(0, item)
        _downloads.value = current
        saveToDisk(current)
    }

    @Synchronized
    fun updateItem(item: DownloadItem) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
            _downloads.value = current
            saveToDisk(current)
        }
    }

    @Synchronized
    fun removeItem(id: String) {
        val current = _downloads.value.toMutableList()
        current.removeAll { it.id == id }
        _downloads.value = current
        saveToDisk(current)
    }

    @Synchronized
    fun clearCompleted() {
        val current = _downloads.value.filter { it.status != DownloadItem.Status.COMPLETED }
        _downloads.value = current
        saveToDisk(current)
    }
}
