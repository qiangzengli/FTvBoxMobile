package okhttp3.dnsoverhttps

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

internal class BootstrapDns(
    private val dnsHostname: String,
    private val dnsServers: List<InetAddress>
) : Dns {

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        if (dnsHostname != hostname) {
            throw UnknownHostException("BootstrapDns called for $hostname instead of $dnsHostname")
        }
        return dnsServers
    }
}
