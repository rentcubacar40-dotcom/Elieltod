package cu.todus.download.engine

import cu.todus.download.data.AppPreferences
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier

data class FileMetadata(
    val fileName: String,
    val contentLength: Long,
    val supportsRanges: Boolean,
    val contentType: String
)

data class PingResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val ip: String,
    val message: String
)

class TodusNetworkClient(private val preferences: AppPreferences) {

    private fun buildClient(): OkHttpClient {
        val nationalIp = preferences.nationalIp
        val sniHost = preferences.sniHost
        val socketFactory = TodusTlsSocketFactory(sniHost)

        val toDusDns = Dns { hostname ->
            if (hostname.equals(sniHost, ignoreCase = true) || hostname.endsWith(".todus.cu", ignoreCase = true)) {
                listOf(InetAddress.getByName(nationalIp))
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }

        val headerInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Host", sniHost)
                .header("User-Agent", preferences.userAgent)
                .header("Accept-Encoding", "identity")
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .dns(toDusDns)
            .sslSocketFactory(socketFactory, socketFactory.trustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .addInterceptor(headerInterceptor)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getClient(): OkHttpClient = buildClient()

    @Throws(IOException::class)
    fun probeFileInfo(url: String): FileMetadata {
        val client = buildClient()
        val defaultName = extractFilenameFromUrl(url)

        // Try HEAD first
        val headReq = Request.Builder()
            .url(url)
            .head()
            .build()

        var length = -1L
        var ranges = false
        var contentType = ""
        var fileName = defaultName

        try {
            client.newCall(headReq).execute().use { response ->
                if (response.isSuccessful) {
                    length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    ranges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    contentType = response.header("Content-Type") ?: ""
                    response.header("Content-Disposition")?.let { disp ->
                        val parsed = extractFilenameFromDisposition(disp)
                        if (parsed.isNotBlank()) fileName = parsed
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to GET bytes=0-0 if HEAD didn't yield size
        if (length <= 0L) {
            val getReq = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .get()
                .build()

            client.newCall(getReq).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && contentRange.contains("/")) {
                        val total = contentRange.substringAfterLast("/").trim().toLongOrNull()
                        if (total != null && total > 0L) {
                            length = total
                            ranges = true
                        }
                    }
                    if (length <= 0L) {
                        length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                    contentType = response.header("Content-Type") ?: contentType
                    response.header("Content-Disposition")?.let { disp ->
                        val parsed = extractFilenameFromDisposition(disp)
                        if (parsed.isNotBlank()) fileName = parsed
                    }
                }
            }
        }

        return FileMetadata(
            fileName = fileName,
            contentLength = length,
            supportsRanges = ranges || length > 0L,
            contentType = contentType
        )
    }

    fun testConnection(): PingResult {
        val ip = preferences.nationalIp
        val sniHost = preferences.sniHost
        val startTime = System.currentTimeMillis()

        return try {
            Socket().use { rawSocket ->
                rawSocket.connect(InetSocketAddress(ip, 443), 5000)
                val factory = TodusTlsSocketFactory(sniHost)
                val sslSocket = factory.createSocket(rawSocket, sniHost, 443, true) as javax.net.ssl.SSLSocket
                sslSocket.startHandshake()
                val latency = System.currentTimeMillis() - startTime
                PingResult(
                    isSuccess = true,
                    latencyMs = latency,
                    ip = ip,
                    message = "Conexión directa nacional establecida (${latency}ms)"
                )
            }
        } catch (e: Exception) {
            PingResult(
                isSuccess = false,
                latencyMs = -1L,
                ip = ip,
                message = e.localizedMessage ?: "Error de conexión a $ip:443"
            )
        }
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val path = url.substringBefore("?").substringBefore("#")
            val rawName = path.substringAfterLast("/")
            if (rawName.isNotBlank()) {
                URLDecoder.decode(rawName, "UTF-8")
            } else {
                "todus_file_${System.currentTimeMillis()}"
            }
        } catch (_: Exception) {
            "todus_download_${System.currentTimeMillis()}"
        }
    }

    private fun extractFilenameFromDisposition(disposition: String): String {
        // e.g. attachment; filename="video.mp4" or filename*=UTF-8''video.mp4
        val utf8Match = Regex("filename\\*=(?:UTF-8''|utf-8'')([^;]+)").find(disposition)
        if (utf8Match != null) {
            return try {
                URLDecoder.decode(utf8Match.groupValues[1].trim('"', '\''), "UTF-8")
            } catch (_: Exception) {
                utf8Match.groupValues[1].trim('"', '\'')
            }
        }
        val normalMatch = Regex("filename=\"?([^\";]+)\"?").find(disposition)
        return normalMatch?.groupValues?.get(1)?.trim('"', '\'') ?: ""
    }
}
