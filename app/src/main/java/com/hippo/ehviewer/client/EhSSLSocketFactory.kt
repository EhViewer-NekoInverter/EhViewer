package com.hippo.ehviewer.client

import com.hippo.ehviewer.Settings
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class EhSSLSocketFactory : SSLSocketFactory() {
    private val factory: SSLSocketFactory

    init {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        factory = context.socketFactory
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val socket = factory.createSocket(s, host, port, autoClose) as SSLSocket
        if (!Settings.doH) return socket
        val params = socket.sslParameters
        params.serverNames = listOf(SNIHostName("eh"))
        socket.sslParameters = params
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return factory.createSocket(host, port)
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket {
        return factory.createSocket(host, port, localHost, localPort)
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return factory.createSocket(host, port)
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket {
        return factory.createSocket(address, port, localAddress, localPort)
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return factory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return factory.supportedCipherSuites
    }
}
