package com.github.tvbox.osc.bean

class IpScanningVo(
    private var hostName: String?,
    private var ip: String?
) {
    fun getHostName(): String? {
        return hostName
    }

    fun setHostName(hostName: String?) {
        this.hostName = hostName
    }

    fun getIp(): String? {
        return ip
    }

    fun setIp(ip: String?) {
        this.ip = ip
    }
}
