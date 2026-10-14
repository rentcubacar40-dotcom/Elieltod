package cu.todus.download.engine

import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Socket Factory con SNI Fronting forzado para tarifa cero en toDus (ETECSA).
 * Conecta directamente a la IP fija nacional 152.207.206.86:443 inyectando el SNI "s3.todus.cu".
 */
class TodusTlsSocketFactory(
    private val sniHost: String = "s3.todus.cu"
) : SSLSocketFactory() {

    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val delegate: SSLSocketFactory

    init {
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf(trustManager), SecureRandom())
        delegate = sc.socketFactory
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(): Socket = patchSocket(delegate.createSocket())

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        patchSocket(delegate.createSocket(s, sniHost, port, autoClose))

    override fun createSocket(host: String, port: Int): Socket =
        patchSocket(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        patchSocket(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        patchSocket(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        patchSocket(delegate.createSocket(address, port, localAddress, localPort))

    private fun patchSocket(s: Socket): Socket {
        if (s is SSLSocket) {
            try {
                val params = s.sslParameters
                params.serverNames = listOf(SNIHostName(sniHost))
                s.sslParameters = params
            } catch (_: Exception) {}
        }
        return s
    }
}
