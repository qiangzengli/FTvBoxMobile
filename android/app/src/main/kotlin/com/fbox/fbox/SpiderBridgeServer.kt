package com.fbox.fbox

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.InputStream
import android.net.Uri
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Shares the Android-only Spider runtime with an iOS client on the same LAN. */
class SpiderBridgeServer(
    context: Context,
    private val invoke: (Map<*, *>) -> String,
    private val proxy: (Map<String, String>) -> Array<Any?>?,
) {
    private val token = context.getSharedPreferences("spider_bridge", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString("token", null) ?: run {
                val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
                val generated = Base64.encodeToString(
                    bytes,
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                )
                preferences.edit().putString("token", generated).apply()
                generated
            }
        }
    private val server = ServerSocket(PORT, 16)
    private val workers = Executors.newFixedThreadPool(8)

    fun start() {
        workers.execute {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    workers.execute { handle(socket) }
                } catch (error: Exception) {
                    if (!server.isClosed) Log.e("FBoxSpiderBridge", "accept failed", error)
                }
            }
        }
    }

    fun address(): String? = lanAddress()?.let { "http://$it:$PORT/$token" }
    fun localAddress(): String = "http://127.0.0.1:$PORT/$token"

    fun close() {
        server.close()
        workers.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { connection ->
            try {
                connection.soTimeout = 15_000
                val input = connection.getInputStream()
                val requestLine = readLine(input) ?: return
                var contentLength = -1
                var headerBytes = requestLine.length
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: error("请求头不完整")
                    headerBytes += line.length
                    require(headerBytes <= 16_384) { "请求头过大" }
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: -1
                    }
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                }
                val method = requestLine.substringBefore(' ')
                val path = requestLine.split(' ').getOrNull(1)
                if (method == "GET" && path == "/$token/health") {
                    respond(connection, 200, "{\"ok\":true}")
                    return
                }
                if ((method == "GET" || method == "HEAD") &&
                    path?.substringBefore('?') == "/$token/proxy") {
                    val uri = Uri.parse("http://localhost$path")
                    val params = mutableMapOf<String, String>()
                    for (name in uri.queryParameterNames) {
                        params[name] = uri.getQueryParameter(name) ?: ""
                    }
                    params.putAll(headers)
                    val result = proxy(params) ?: error("Spider 未返回代理内容")
                    respondProxy(connection, result, method == "GET")
                    return
                }
                if (method != "POST" || path != "/$token/invoke") {
                    respond(connection, 404, "{\"error\":\"未知接口\"}")
                    return
                }
                require(contentLength in 1..2_000_000) { "请求内容长度无效" }
                val bytes = ByteArray(contentLength)
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) error("请求内容不完整")
                    offset += count
                }
                val parsed = Gson().fromJson(String(bytes, Charsets.UTF_8), Map::class.java)
                require(parsed is Map<*, *>) { "Spider 参数无效" }
                val value = invoke(parsed)
                respond(connection, 200, org.json.JSONObject().put("value", value).toString())
            } catch (error: Throwable) {
                Log.e("FBoxSpiderBridge", "request failed", error)
                runCatching {
                    respond(
                        connection,
                        500,
                        org.json.JSONObject().put("error", error.message ?: "Spider 请求失败").toString(),
                    )
                }
            }
        }
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val heading = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(heading.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    private fun respondProxy(socket: Socket, result: Array<Any?>, sendBody: Boolean) {
        require(result.size >= 3) { "Spider 代理响应无效" }
        val status = (result[0] as? Number)?.toInt() ?: 200
        val contentType = result[1]?.toString() ?: "application/octet-stream"
        val stream = result[2] as? InputStream ?: error("Spider 代理未返回数据流")
        val output = socket.getOutputStream()
        val heading = StringBuilder("HTTP/1.1 $status ${if (status == 206) "Partial Content" else "OK"}\r\n")
            .append("Content-Type: $contentType\r\n")
        if (sendBody) heading.append("Transfer-Encoding: chunked\r\n")
        heading.append("Connection: close\r\n")
        val extra = result.getOrNull(3) as? Map<*, *>
        extra?.forEach { (key, value) ->
            val name = key?.toString() ?: return@forEach
            val text = value?.toString() ?: return@forEach
            if (name.contains('\r') || name.contains('\n') ||
                text.contains('\r') || text.contains('\n') ||
                name.equals("Content-Length", ignoreCase = true) ||
                name.equals("Transfer-Encoding", ignoreCase = true)) return@forEach
            heading.append("$name: $text\r\n")
        }
        heading.append("\r\n")
        output.write(heading.toString().toByteArray(Charsets.US_ASCII))
        stream.use { input ->
            if (!sendBody) return@use
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write("${count.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
                output.write(buffer, 0, count)
                output.write("\r\n".toByteArray(Charsets.US_ASCII))
            }
        }
        if (!sendBody) return
        output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (line.size() <= 8192) {
            val value = input.read()
            if (value < 0) return if (line.size() == 0) null else error("请求行不完整")
            if (value == 10) return line.toString(Charsets.ISO_8859_1.name())
                .trimEnd('\r')
            line.write(value)
        }
        error("请求行过长")
    }

    private fun lanAddress(): String? {
        var fallback: String? = null
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    if (network.name == "wlan0") return address.hostAddress
                    fallback = fallback ?: address.hostAddress
                }
            }
        }
        return fallback
    }

    companion object {
        const val PORT = 8793
    }
}
