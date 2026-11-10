package com.github.catvod.net

import androidx.collection.ArrayMap
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object OkHttp {

    private val TIMEOUT = TimeUnit.SECONDS.toMillis(30)
    private var dns: OkDns? = null
    private var client: OkHttpClient? = null

    private val TRUST_ALL_CERT = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Synchronized
    @JvmStatic
    fun dns(): OkDns {
        if (dns == null) dns = OkDns()
        return dns!!
    }

    @Synchronized
    @JvmStatic
    fun client(): OkHttpClient {
        if (client != null) return client!!
        val base = OkGoHelper.getDefaultClient()
        if (base != null) {
            val c = base.newBuilder().dns(dns()).build()
            client = c
            return c
        }
        val builder = OkHttpClient.Builder()
            .dns(dns())
            .proxySelector(OkGoHelper.proxySelector())
            .proxyAuthenticator(OkGoHelper.proxyAuthenticator())
            .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
        setOkHttpSsl(builder)
        val c = builder.build()
        client = c
        return c
    }

    @JvmStatic
    fun player(): OkHttpClient {
        return client()
    }

    @JvmStatic
    fun client(timeout: Long): OkHttpClient {
        return client().newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()
    }

    @JvmStatic
    fun noRedirect(): OkHttpClient {
        return noRedirect(TIMEOUT)
    }

    @JvmStatic
    fun noRedirect(timeout: Long): OkHttpClient {
        var base = OkGoHelper.getNoRedirectClient()
        if (base == null) base = client()
        return base.newBuilder()
            .dns(dns())
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Synchronized
    @JvmStatic
    fun reset() {
        client = null
        dns = null
    }

    @Synchronized
    @JvmStatic
    fun resetClient() {
        client = null
    }

    @JvmStatic
    fun client(redirect: Boolean, timeout: Long): OkHttpClient {
        return if (redirect) client(timeout) else noRedirect(timeout)
    }

    @JvmStatic
    fun string(url: String?): String {
        if (url == null || !url.startsWith("http")) return ""
        return try {
            newCall(url).execute().use { res ->
                res.body()?.string() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun string(url: String?, timeout: Long): String {
        if (url == null || !url.startsWith("http")) return ""
        return try {
            newCall(client(timeout), url).execute().use { res ->
                res.body()?.string() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun string(url: String?, headers: Map<String, String>?): String {
        if (url == null || !url.startsWith("http")) return ""
        return try {
            newCall(url, headers).execute().use { res ->
                res.body()?.string() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun newCall(url: String): Call {
        return client().newCall(Request.Builder().url(url).build())
    }

    @JvmStatic
    fun newCall(url: String, tag: String?): Call {
        return client().newCall(Request.Builder().url(url).tag(tag).build())
    }

    @JvmStatic
    fun newCall(client: OkHttpClient, url: String): Call {
        return client.newCall(Request.Builder().url(url).build())
    }

    @JvmStatic
    fun newCall(client: OkHttpClient, url: String, tag: String?): Call {
        return client.newCall(Request.Builder().url(url).tag(tag).build())
    }

    @JvmStatic
    fun newCall(url: String, headers: Map<String, String>?): Call {
        return client().newCall(Request.Builder().url(url).headers(headers(headers)).build())
    }

    @JvmStatic
    fun newCall(url: String, headers: Map<String, String>?, params: ArrayMap<String, String>?): Call {
        return client().newCall(Request.Builder().url(buildUrl(url, params)).headers(headers(headers)).build())
    }

    @JvmStatic
    fun newCall(url: String, headers: Map<String, String>?, body: RequestBody): Call {
        return client().newCall(Request.Builder().url(url).headers(headers(headers)).post(body).build())
    }

    @JvmStatic
    fun newCall(url: String, body: RequestBody, tag: String?): Call {
        return client().newCall(Request.Builder().url(url).post(body).tag(tag).build())
    }

    @JvmStatic
    fun newCall(client: OkHttpClient, url: String, body: RequestBody): Call {
        return client.newCall(Request.Builder().url(url).post(body).build())
    }

    @JvmStatic
    fun cancel(tag: String?) {
        cancel(client(), tag)
    }

    @JvmStatic
    fun cancel(client: OkHttpClient?, tag: String?) {
        if (client == null || tag == null) return
        for (call in client.dispatcher().queuedCalls()) if (tag == call.request().tag()) call.cancel()
        for (call in client.dispatcher().runningCalls()) if (tag == call.request().tag()) call.cancel()
    }

    @JvmStatic
    fun cancelAll() {
        cancelAll(client())
    }

    @JvmStatic
    fun cancelAll(client: OkHttpClient?) {
        client?.dispatcher()?.cancelAll()
    }

    @JvmStatic
    fun toBody(params: ArrayMap<String, String>?): FormBody {
        val body = FormBody.Builder()
        if (params != null) {
            for ((key, value) in params) {
                body.add(key, value)
            }
        }
        return body.build()
    }

    private fun headers(headers: Map<String, String>?): Headers {
        return if (headers == null) Headers.Builder().build() else Headers.of(headers)
    }

    private fun setOkHttpSsl(builder: OkHttpClient.Builder) {
        try {
            val sslSocketFactory: SSLSocketFactory = SSLSocketFactoryCompat(TRUST_ALL_CERT)
            builder.sslSocketFactory(sslSocketFactory, TRUST_ALL_CERT)
            builder.hostnameVerifier { _, _ -> true }
        } catch (ignored: Throwable) {
        }
    }

    private fun buildUrl(url: String, params: ArrayMap<String, String>?): HttpUrl {
        val builder = HttpUrl.parse(url)!!.newBuilder()
        if (params != null) {
            for ((key, value) in params) {
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build()
    }
}
