package com.github.catvod

import com.github.tvbox.osc.server.RemoteServer

object Proxy {

    @JvmStatic
    private var port: Int = RemoteServer.serverPort

    @JvmStatic
    fun set(port: Int) {
        Proxy.port = port
    }

    @JvmStatic
    fun getPort(): Int {
        return if (port > 0) port else RemoteServer.serverPort
    }

    @JvmStatic
    fun getUrl(local: Boolean): String {
        return "http://" + (if (local) "127.0.0.1" else getIp()) + ":" + getPort() + "/proxy"
    }

    @JvmStatic
    private fun getIp(): String {
        return try {
            RemoteServer.getLocalIPAddress(com.github.tvbox.osc.base.App.getInstance())
        } catch (th: Throwable) {
            "127.0.0.1"
        }
    }
}
