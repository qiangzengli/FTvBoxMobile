package com.github.tvbox.osc.util

import android.graphics.Bitmap
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.ProxyRule
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader
import com.github.tvbox.osc.player.danmu.Parser
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat
import com.github.tvbox.osc.util.net.OkProxySelector
import com.github.tvbox.osc.util.net.ProxyAuthenticator
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.lzy.okgo.OkGo
import com.lzy.okgo.https.HttpsUtils
import com.lzy.okgo.interceptor.HttpLoggingInterceptor
import com.lzy.okgo.model.HttpHeaders
import com.orhanobut.hawk.Hawk
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.internal.Version
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object OkGoHelper {
    const val DEFAULT_MILLISECONDS: Long = 20000

    private const val dnsConfigJson = ("["
            + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
            + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
            + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
            + "]")

    @JvmField
    var ItvClient: OkHttpClient? = null
    private var proxySelector: OkProxySelector? = null
    private var proxyAuthenticator: ProxyAuthenticator? = null

    @JvmField
    var dnsOverHttps: DnsOverHttps? = null
    @JvmField
    var dnsHttpsList: ArrayList<String> = ArrayList()
    @JvmField
    var is_doh: Boolean = false
    @JvmField
    var myHosts: Map<String, String>? = null

    @JvmField
    var defaultClient: OkHttpClient? = null
    @JvmField
    var noRedirectClient: OkHttpClient? = null

    @Synchronized
    @JvmStatic
    fun proxySelector(): OkProxySelector {
        if (proxySelector == null) proxySelector = OkProxySelector()
        return proxySelector!!
    }

    @Synchronized
    @JvmStatic
    fun proxyAuthenticator(): ProxyAuthenticator {
        if (proxyAuthenticator == null) proxyAuthenticator = ProxyAuthenticator(proxySelector())
        return proxyAuthenticator!!
    }

    @Synchronized
    @JvmStatic
    fun setProxyList(proxyRules: List<ProxyRule>?) {
        proxySelector().clear()
        if (!proxyRules.isNullOrEmpty()) proxySelector().addAll(proxyRules)
        com.github.catvod.net.OkHttp.reset()
    }

    @JvmStatic
    fun initExoOkHttpClient() {
        val base = getDefaultClient()
        val builder = base?.newBuilder() ?: OkHttpClient.Builder()
        val loggingInterceptor = HttpLoggingInterceptor("OkExoPlayer")

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.setColorLevel(Level.INFO)
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE)
            loggingInterceptor.setColorLevel(Level.OFF)
        }
        builder.addInterceptor(loggingInterceptor)

        builder.retryOnConnectionFailure(true)
        builder.followRedirects(true)
        builder.followSslRedirects(true)
        builder.proxySelector(proxySelector())
        builder.proxyAuthenticator(proxyAuthenticator())

        try {
            setOkHttpSsl(builder)
        } catch (th: Throwable) {
            th.printStackTrace()
        }

        builder.dns(CustomDns())
        ItvClient = builder.build()

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient)
    }

    @JvmStatic
    fun getDohUrl(type: Int): String {
        var json = Hawk.get(HawkConfig.DOH_JSON, "")
        if (json.isEmpty()) json = dnsConfigJson
        val jsonArray = JsonParser.parseString(json).asJsonArray
        if (type >= 1 && type < dnsHttpsList.size) {
            val dnsConfig = jsonArray.get(type - 1).asJsonObject
            return dnsConfig.get("url").asString
        }
        return ""
    }

    @JvmStatic
    fun setDnsList() {
        dnsHttpsList.clear()
        var json = Hawk.get(HawkConfig.DOH_JSON, "")
        if (json.isEmpty()) json = dnsConfigJson
        val jsonArray = JsonParser.parseString(json).asJsonArray
        dnsHttpsList.add("关闭")
        for (i in 0 until jsonArray.size()) {
            val dnsConfig = jsonArray.get(i).asJsonObject
            val name = if (dnsConfig.has("name")) dnsConfig.get("name").asString else "Unknown Name"
            dnsHttpsList.add(name)
        }
        if (Hawk.get(HawkConfig.DOH_URL, 0) + 1 > dnsHttpsList.size) Hawk.put(HawkConfig.DOH_URL, 0)
        myHosts = ApiConfig.get().getMyHost()
    }

    private fun DohIps(ips: JsonArray?): List<InetAddress> {
        val inetAddresses = ArrayList<InetAddress>()
        if (ips != null) {
            for (j in 0 until ips.size()) {
                try {
                    val inetAddress = InetAddress.getByName(ips.get(j).asString)
                    inetAddresses.add(inetAddress)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return inetAddresses
    }

    @JvmStatic
    fun initDnsOverHttps() {
        var dohSelector: Int = Hawk.get(HawkConfig.DOH_URL, 0)
        var ips: JsonArray? = null
        try {
            dnsHttpsList.clear()
            dnsHttpsList.add("关闭")
            var json = Hawk.get(HawkConfig.DOH_JSON, "")
            if (json.isEmpty()) json = dnsConfigJson
            val jsonArray = JsonParser.parseString(json).asJsonArray
            if (dohSelector > jsonArray.size()) {
                Hawk.put(HawkConfig.DOH_URL, 0)
                dohSelector = 0
            }
            for (i in 0 until jsonArray.size()) {
                val dnsConfig = jsonArray.get(i).asJsonObject
                val name = if (dnsConfig.has("name")) dnsConfig.get("name").asString else "Unknown Name"
                dnsHttpsList.add(name)
                if (dohSelector == i + 1) {
                    ips = if (dnsConfig.has("ips")) dnsConfig.getAsJsonArray("ips") else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val builder = OkHttpClient.Builder()
        builder.proxySelector(proxySelector())
        builder.proxyAuthenticator(proxyAuthenticator())
        val loggingInterceptor = HttpLoggingInterceptor("OkExoPlayer")
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.setColorLevel(Level.INFO)
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE)
            loggingInterceptor.setColorLevel(Level.OFF)
        }
        builder.addInterceptor(loggingInterceptor)
        try {
            setOkHttpSsl(builder)
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        builder.cache(Cache(File(App.getInstance().cacheDir.absolutePath, "dohcache"), 100 * 1024 * 1024))
        val dohClient = builder.build()
        val dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0))
        val builderDoh = DnsOverHttps.Builder()
            .client(dohClient)
            .url(if (dohUrl.isEmpty()) null else HttpUrl.get(dohUrl))
        if (ips != null && dohUrl != "https://doh.pub/dns-query") {
            builderDoh.bootstrapDnsHosts(DohIps(ips))
        }
        dnsOverHttps = builderDoh.build()
    }

    class CustomDns : Dns {
        private var map: ConcurrentHashMap<String, List<InetAddress>>? = null
        private val excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245"

        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> {
            var targetHost = hostname
            var hosts = myHosts
            if (hosts == null) hosts = ApiConfig.get().getMyHost()
            if (hosts != null && hosts.isNotEmpty() && hosts.containsKey(targetHost)) {
                targetHost = hosts[targetHost] ?: targetHost
            }
            return if (isValidIpAddress(targetHost)) {
                Collections.singletonList(InetAddress.getByName(targetHost))
            } else {
                val dns = dnsOverHttps ?: Dns.SYSTEM
                dns.lookup(targetHost)
            }
        }

        @Synchronized
        @Throws(UnknownHostException::class)
        fun mapHosts(hosts: Map<String, String>) {
            val newMap = ConcurrentHashMap<String, List<InetAddress>>()
            map = newMap
            for ((key, value) in hosts) {
                if (isValidIpAddress(value)) {
                    newMap[key] = Collections.singletonList(InetAddress.getByName(value))
                } else {
                    newMap[key] = getAllByName(value)
                }
            }
        }

        private fun getAllByName(host: String): List<InetAddress> {
            return try {
                val allAddresses = InetAddress.getAllByName(host)
                if (excludeIps.isEmpty()) return Arrays.asList(*allAddresses)
                val validAddresses = ArrayList<InetAddress>()
                val excludeIpsSet = HashSet<String>()
                for (ip in excludeIps.split(",".toRegex())) {
                    excludeIpsSet.add(ip.trim())
                }
                for (address in allAddresses) {
                    if (!excludeIpsSet.contains(address.hostAddress)) {
                        validAddresses.add(address)
                    }
                }
                validAddresses
            } catch (e: Exception) {
                ArrayList()
            }
        }

        private fun isValidIpAddress(str: String): Boolean {
            return if (str.indexOf('.') > 0) isValidIPv4(str) else str.indexOf(':') > 0
        }

        private fun isValidIPv4(str: String): Boolean {
            val parts = str.split("\\.".toRegex()).toTypedArray()
            if (parts.size != 4) return false
            for (part in parts) {
                try {
                    part.toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
            }
            return true
        }
    }

    @JvmStatic
    fun getDefaultClient(): OkHttpClient? {
        return defaultClient
    }

    @JvmStatic
    fun getNoRedirectClient(): OkHttpClient? {
        return noRedirectClient
    }

    @JvmStatic
    fun getItvClient(): OkHttpClient? {
        return ItvClient
    }

    @JvmStatic
    fun init() {
        initDnsOverHttps()

        val builder = OkHttpClient.Builder()
        val loggingInterceptor = HttpLoggingInterceptor("OkGo")

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.setColorLevel(Level.INFO)
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE)
            loggingInterceptor.setColorLevel(Level.OFF)
        }

        builder.addInterceptor(loggingInterceptor)
        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)

        builder.dns(CustomDns())
        builder.proxySelector(proxySelector())
        builder.proxyAuthenticator(proxyAuthenticator())
        try {
            setOkHttpSsl(builder)
        } catch (th: Throwable) {
            th.printStackTrace()
        }

        HttpHeaders.setUserAgent(Version.userAgent())

        val okHttpClient = builder.build()
        OkGo.getInstance().okHttpClient = okHttpClient
        defaultClient = okHttpClient

        builder.followRedirects(false)
        builder.followSslRedirects(false)
        noRedirectClient = builder.build()

        initExoOkHttpClient()
        initPicasso(okHttpClient)
    }

    @Synchronized
    @JvmStatic
    fun reloadDns() {
        initDnsOverHttps()

        val builder = OkHttpClient.Builder()
        val loggingInterceptor = HttpLoggingInterceptor("OkGo")

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.setColorLevel(Level.INFO)
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE)
            loggingInterceptor.setColorLevel(Level.OFF)
        }

        builder.addInterceptor(loggingInterceptor)
        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)

        builder.dns(CustomDns())
        builder.proxySelector(proxySelector())
        builder.proxyAuthenticator(proxyAuthenticator())
        try {
            setOkHttpSsl(builder)
        } catch (th: Throwable) {
            th.printStackTrace()
        }

        HttpHeaders.setUserAgent(Version.userAgent())

        val okHttpClient = builder.build()
        OkGo.getInstance().okHttpClient = okHttpClient
        defaultClient = okHttpClient

        builder.followRedirects(false)
        builder.followSslRedirects(false)
        noRedirectClient = builder.build()

        initExoOkHttpClient()
        Parser.resetHttpClient()
        com.github.catvod.net.OkHttp.resetClient()
    }

    @JvmStatic
    fun initPicasso(client: OkHttpClient) {
        client.dispatcher().setMaxRequestsPerHost(10)
        val downloader = MyOkhttpDownLoader(client)
        val picasso = Picasso.Builder(App.getInstance())
            .downloader(downloader)
            .defaultBitmapConfig(Bitmap.Config.RGB_565)
            .build()
        Picasso.setSingletonInstance(picasso)
    }

    private fun setOkHttpSsl(builder: OkHttpClient.Builder) {
        try {
            val trustAllCert = object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
            val sslSocketFactory: SSLSocketFactory = SSLSocketFactoryCompat(trustAllCert)
            builder.sslSocketFactory(sslSocketFactory, trustAllCert)
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
