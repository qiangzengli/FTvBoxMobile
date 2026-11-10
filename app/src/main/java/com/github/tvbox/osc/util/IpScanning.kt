package com.github.tvbox.osc.util

import com.github.tvbox.osc.bean.IpScanningVo
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.ArrayList
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class IpScanning {
    private var ipScanningVos: MutableList<IpScanningVo> = ArrayList()

    var corePoolSize = 5
    var maximumPoolSize = 10

    private val threadPool = ThreadPoolExecutor(
        corePoolSize,
        maximumPoolSize,
        3,
        TimeUnit.NANOSECONDS,
        LinkedBlockingQueue(10),
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    /**
     * 通过IP扫描对应网段中可以使用的网段
     * @param ips 输入的IP
     */
    fun search(ips: String, all: Boolean): List<IpScanningVo> {
        ipScanningVos.clear()
        val divisionIp = ips.lastIndexOf(".")
        val substring = ips.substring(0, divisionIp + 1)

        val last = ips.substring(divisionIp + 1)
        var end = last.toInt() + 30
        end = if (end > 255) 255 else end
        if (all) end = 255
        val total = end

        val queue: BlockingQueue<IpScanningVo> = ArrayBlockingQueue(total)
        for (i in 1 until total) {
            val iip = substring + i
            threadPool.submit(PingIp(iip, queue))
        }
        threadPool.shutdown()
        while (!threadPool.isTerminated) {
        }
        ipScanningVos = ArrayList(queue)
        return ipScanningVos
    }

    private class PingIp(private val ip: String, private val array: Queue<IpScanningVo>) : Runnable {
        override fun run() {
            var addip: InetAddress? = null
            try {
                addip = InetAddress.getByName(ip)
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            }
            var status = false
            try {
                if (addip != null) {
                    status = addip.isReachable(1000)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (status && addip != null) {
                val ipScanning = IpScanningVo(addip.hostName, ip)
                LOG.i("IP地址为:$ip\t\t设备名称为: ${addip.hostName}\t\t是否可用: ${if (status) "可用" else "不可用"}")
                array.add(ipScanning)
            }
        }
    }
}
