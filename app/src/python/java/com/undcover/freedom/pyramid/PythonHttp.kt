package com.undcover.freedom.pyramid

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * Uses the app's configured OkHttp stack for Python sites which reject the
 * embedded Python requests TLS/network fingerprint.
 */
object PythonHttp {
    private val JSON = MediaType.parse("application/json; charset=utf-8")

    @JvmStatic
    fun request(method: String, url: String, headersJson: String?, body: String?, followRedirects: Boolean): String {
        var result = JSONObject()
        try {
            val builder = Request.Builder().url(url)
            if (!headersJson.isNullOrEmpty()) {
                val headers = JSONObject(headersJson)
                val keys = headers.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    builder.header(key, headers.optString(key))
                }
            }

            var requestBody: RequestBody? = null
            if (!"GET".equals(method, ignoreCase = true) && !"HEAD".equals(method, ignoreCase = true)) {
                requestBody = RequestBody.create(JSON, body ?: "")
            }
            val request = builder.method(method, requestBody).build()
            var client = com.github.catvod.net.OkHttp.client()
            if (!followRedirects) {
                client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            }
            client.newCall(request).execute().use { response ->
                result.put("status_code", response.code())
                val responseHeaders = JSONObject()
                for (name in response.headers().names()) {
                    responseHeaders.put(name, response.header(name))
                }
                result.put("headers", responseHeaders)
                result.put("text", response.body()?.string() ?: "")
            }
        } catch (error: Throwable) {
            result = JSONObject()
            try {
                result.put("error", error.toString())
            } catch (ignored: Exception) {
            }
        }
        return result.toString()
    }
}
