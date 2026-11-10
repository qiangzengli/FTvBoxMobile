package com.github.tvbox.osc.bean

import android.net.Uri
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.ArrayList
import java.util.Collections

class ProxyRule : Comparable<ProxyRule> {

    @SerializedName("name")
    private var name: String? = null
    @SerializedName("hosts")
    private var hosts: MutableList<String>? = null
    @SerializedName("urls")
    private var urls: MutableList<String>? = null

    private var proxies: MutableList<Proxy>? = null
    private var uris: MutableList<Uri>? = null
    private var wildcard: Boolean = false

    fun init() {
        wildcard = false
        for (host in getHosts()) {
            if (host.indexOf('*') >= 0) {
                wildcard = true
                break
            }
        }
        val uList = ArrayList<Uri>()
        for (url in getUrls()) {
            if (TextUtils.isEmpty(url)) continue
            val uri = Uri.parse(url)
            if (isValid(uri)) uList.add(uri)
        }
        uris = uList
        val pList = ArrayList<Proxy>()
        for (uri in uList) {
            val proxy = create(uri)
            if (proxy != null) pList.add(proxy)
        }
        proxies = pList
    }

    fun getName(): String {
        return if (TextUtils.isEmpty(name)) "" else name!!
    }

    fun getHosts(): List<String> {
        return hosts ?: Collections.emptyList()
    }

    fun getUrls(): List<String> {
        return urls ?: Collections.emptyList()
    }

    fun getProxies(): List<Proxy> {
        return proxies ?: Collections.emptyList()
    }

    fun getUserInfo(host: String?): String? {
        if (uris == null || host == null) return null
        for (uri in uris!!) {
            if (uri.host == null) continue
            if (host.equals(uri.host, ignoreCase = true)) {
                val userInfo = uri.userInfo
                if (!TextUtils.isEmpty(userInfo)) return userInfo
            }
        }
        return null
    }

    private fun isValid(uri: Uri?): Boolean {
        return uri != null && uri.scheme != null && uri.host != null && uri.port > 0
    }

    private fun create(uri: Uri): Proxy? {
        val address = InetSocketAddress.createUnresolved(uri.host, uri.port)
        if (isScheme(uri, "http")) return Proxy(Proxy.Type.HTTP, address)
        if (isScheme(uri, "socks")) return Proxy(Proxy.Type.SOCKS, address)
        return null
    }

    private fun isScheme(uri: Uri, scheme: String): Boolean {
        return uri.scheme != null && uri.scheme!!.startsWith(scheme)
    }

    override fun compareTo(other: ProxyRule): Int {
        return this.wildcard.compareTo(other.wildcard)
    }

    companion object {
        @JvmStatic
        fun arrayFrom(element: JsonElement?): List<ProxyRule> {
            return try {
                val listType = object : TypeToken<List<ProxyRule>>() {}.type
                val items: List<ProxyRule>? = Gson().fromJson(element, listType)
                items ?: Collections.emptyList()
            } catch (e: Exception) {
                Collections.emptyList()
            }
        }
    }
}
