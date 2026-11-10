package com.github.tvbox.osc.util.thunder

import android.net.Uri
import android.text.TextUtils
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.util.LocalIPAddress
import com.p2p.P2PClass
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder

object Jianpian {

    @JvmStatic
    fun JPUrlDec(url: String): String {
        val p2p = App.getp2p()
        if (p2p != null) {
            return try {
                val decode = URLDecoder.decode(url, "UTF-8")
                val split = decode.split("\\|".toRegex()).toTypedArray()
                var replace = split[0].replace("xg://", "ftp://")
                if (replace.contains("xgplay://")) {
                    replace = split[0].replace("xgplay://", "ftp://")
                }
                val currentBurl = App.burl
                if (!currentBurl.isNullOrEmpty()) {
                    p2p.P2Pdoxpause(currentBurl.toByteArray(charset("GBK")))
                    p2p.P2Pdoxdel(currentBurl.toByteArray(charset("GBK")))
                }
                App.burl = replace
                p2p.P2Pdoxstart(replace.toByteArray(charset("GBK")))
                p2p.P2Pdoxadd(replace.toByteArray(charset("GBK")))
                "http://" + LocalIPAddress.getIP(App.getInstance()) + ":" + P2PClass.port + "/" + URLEncoder.encode(
                    Uri.parse(replace).lastPathSegment,
                    "GBK"
                )
            } catch (e: Exception) {
                e.localizedMessage ?: ""
            }
        } else {
            return ""
        }
    }

    @JvmStatic
    fun finish() {
        val p2p = App.getp2p()
        val currentBurl = App.burl
        if (!currentBurl.isNullOrEmpty() && p2p != null) {
            try {
                p2p.P2Pdoxpause(currentBurl.toByteArray(charset("GBK")))
                p2p.P2Pdoxdel(currentBurl.toByteArray(charset("GBK")))
                App.burl = ""
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun isJpUrl(url: String): Boolean {
        return url.startsWith("tvbox-xg:") || (Thunder.isFtp(url) && url.contains("gbl.114s"))
    }
}
