package com.github.tvbox.osc.dlna

import org.fourthline.cling.model.message.Connection
import org.fourthline.cling.model.message.StreamRequestMessage
import org.fourthline.cling.model.message.StreamResponseMessage
import org.fourthline.cling.model.message.UpnpHeaders
import org.fourthline.cling.model.message.UpnpMessage
import org.fourthline.cling.model.message.UpnpRequest
import org.fourthline.cling.protocol.ProtocolFactory
import org.fourthline.cling.transport.Router
import org.fourthline.cling.transport.spi.InitializationException
import org.fourthline.cling.transport.spi.StreamServer
import org.fourthline.cling.transport.spi.StreamServerConfiguration
import org.fourthline.cling.transport.spi.UpnpStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.HashMap

class SocketHttpStreamServer(private val configuration: Configuration) :
    StreamServer<SocketHttpStreamServer.Configuration> {

    companion object {
        private val ISO_8859_1: Charset = Charset.forName("ISO-8859-1")
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var stopped = false
    private var router: Router? = null

    override fun getConfiguration(): Configuration {
        return configuration
    }

    @Throws(InitializationException::class)
    override fun init(bindAddress: InetAddress, router: Router) {
        this.router = router
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bindAddress, configuration.listenPort), 50)
            serverSocket = socket
        } catch (e: IOException) {
            throw InitializationException("Could not bind HTTP server socket on $bindAddress", e)
        }
    }

    override fun getPort(): Int {
        return serverSocket?.localPort ?: -1
    }

    override fun stop() {
        stopped = true
        try {
            serverSocket?.close()
        } catch (ignored: IOException) {
        }
    }

    override fun run() {
        while (!stopped) {
            try {
                val socket = serverSocket?.accept() ?: break
                socket.soTimeout = 30000
                router?.received(SocketUpnpStream(router!!.protocolFactory, socket))
            } catch (e: SocketException) {
                break
            } catch (ignored: IOException) {
            }
        }
    }

    private class SocketUpnpStream(protocolFactory: ProtocolFactory, private val socket: Socket) :
        UpnpStream(protocolFactory) {

        override fun run() {
            try {
                val inputStream = socket.getInputStream()
                val requestLine = readLine(inputStream)
                val parts = requestLine.split(" ".toRegex(), limit = 3).toTypedArray()
                if (requestLine.isEmpty() || parts.size < 2) {
                    socket.close()
                    return
                }
                val headers = readHeaders(inputStream)
                val requestMessage = buildRequestMessage(parts[0], parts[1], headers)
                readBodyInto(inputStream, requestMessage, headers)
                val responseMessage = process(requestMessage)
                val outputStream = socket.getOutputStream()
                writeResponse(outputStream, responseMessage)
                outputStream.flush()
                responseSent(responseMessage)
            } catch (e: Exception) {
                responseException(e)
            } finally {
                try {
                    socket.close()
                } catch (ignored: IOException) {
                }
            }
        }

        @Throws(IOException::class)
        private fun readLine(inputStream: InputStream): String {
            val sb = java.lang.StringBuilder()
            var previous = -1
            var value: Int
            while (inputStream.read().also { value = it } != -1) {
                if (previous == '\r'.code && value == '\n'.code) {
                    sb.deleteCharAt(sb.length - 1)
                    return sb.toString()
                }
                sb.append(value.toChar())
                previous = value
            }
            return sb.toString()
        }

        @Throws(IOException::class)
        private fun readHeaders(inputStream: InputStream): Map<String, List<String>> {
            val headers: MutableMap<String, MutableList<String>> = HashMap()
            var line: String
            while (readLine(inputStream).also { line = it }.isNotEmpty()) {
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                var values = headers[key]
                if (values == null) {
                    values = ArrayList()
                    headers[key] = values
                }
                values.add(value)
            }
            return headers
        }

        private fun buildRequestMessage(
            method: String,
            rawUri: String,
            headers: Map<String, List<String>>
        ): StreamRequestMessage {
            val message = StreamRequestMessage(UpnpRequest.Method.getByHttpName(method), URI.create(rawUri))
            message.connection = SocketConnection(socket)
            message.headers = UpnpHeaders(headers)
            return message
        }

        @Throws(IOException::class)
        private fun readBodyInto(
            inputStream: InputStream,
            message: StreamRequestMessage,
            headers: Map<String, List<String>>
        ) {
            val lengthHeaders = headers["content-length"]
            if (lengthHeaders == null || lengthHeaders.isEmpty()) return
            val length = lengthHeaders[0].trim().toInt()
            if (length <= 0) return
            val body = ByteArray(length)
            var offset = 0
            var read: Int = 0
            while (offset < length && inputStream.read(body, offset, length - offset).also { read = it } != -1) {
                offset += read
            }
            if (message.isContentTypeMissingOrText) {
                message.setBodyCharacters(body)
            } else {
                message.setBody(UpnpMessage.BodyType.BYTES, body)
            }
        }

        @Throws(IOException::class)
        private fun writeResponse(outputStream: OutputStream, message: StreamResponseMessage?) {
            if (message == null) {
                writeStatusLine(outputStream, 404, "Not Found")
                writeHeader(outputStream, "Content-Length", "0")
                writeEndHeaders(outputStream)
                return
            }
            writeStatusLine(outputStream, message.operation.statusCode, message.operation.statusMessage)
            for ((key, value1) in message.headers.entries) {
                if (key == null || value1 == null) continue
                for (value in value1) {
                    writeHeader(outputStream, key, value)
                }
            }
            val body = if (message.hasBody()) message.bodyBytes else null
            writeHeader(outputStream, "Content-Length", (body?.size ?: 0).toString())
            writeEndHeaders(outputStream)
            if (body != null && body.isNotEmpty()) outputStream.write(body)
        }

        @Throws(IOException::class)
        private fun writeStatusLine(outputStream: OutputStream, code: Int, reason: String?) {
            outputStream.write(("HTTP/1.1 $code " + (reason ?: "") + "\r\n").toByteArray(ISO_8859_1))
        }

        @Throws(IOException::class)
        private fun writeHeader(outputStream: OutputStream, name: String, value: String) {
            outputStream.write(("$name: $value\r\n").toByteArray(ISO_8859_1))
        }

        @Throws(IOException::class)
        private fun writeEndHeaders(outputStream: OutputStream) {
            outputStream.write("\r\n".toByteArray(ISO_8859_1))
        }
    }

    private class SocketConnection(private val socket: Socket) : Connection {
        override fun isOpen(): Boolean {
            return !socket.isClosed
        }

        override fun getRemoteAddress(): InetAddress {
            return socket.inetAddress
        }

        override fun getLocalAddress(): InetAddress {
            return socket.localAddress
        }
    }

    class Configuration(private val listenPort: Int) : StreamServerConfiguration {
        override fun getListenPort(): Int {
            return listenPort
        }
    }
}
