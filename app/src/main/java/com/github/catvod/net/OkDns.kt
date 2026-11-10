package com.github.catvod.net

import com.github.tvbox.osc.util.OkGoHelper
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class OkDns : Dns {

    private val hosts = ConcurrentHashMap<String, String>()

    fun addAll(hosts: List<String?>?) {
        if (hosts == null) return
        for (host in hosts) {
            if (host == null) continue
            val splits = host.split("=".toRegex(), limit = 2).toTypedArray()
            if (splits.size == 2) this.hosts[splits[0].trim()] = splits[1].trim()
        }
    }

    fun clear() {
        hosts.clear()
    }

    private fun get(hostname: String): String {
        val target = hosts[hostname]
        if (target != null) return target
        for ((key, value) in hosts) {
            if (hostname.contains(key)) return value
        }
        return hostname
    }

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val dns = OkGoHelper.dnsOverHttps ?: Dns.SYSTEM
        return dns.lookup(get(hostname))
    }
}
