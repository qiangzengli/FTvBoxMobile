package com.p2p

import androidx.annotation.Keep
import java.io.File

@Keep
class P2PClass(str: String) {

    @JvmField
    var path: String? = null

    private inner class Init(val cardPath: String) : Thread() {
        override fun run() {
            this@P2PClass.path = "$cardPath/jpali"
            val file = File(this@P2PClass.path!!)
            if (!file.exists()) {
                file.mkdirs()
            }
            port = doxstarthttpd("TEST3E63BAAECDAA79BEAA91853490A69F08".toByteArray(), cardPath.toByteArray())
        }
    }

    init {
        path = "$str/jpali"
        val file = File(path!!)
        if (!file.exists()) {
            file.mkdirs()
        }
        port = doxstarthttpd("TEST3E63BAAECDAA79BEAA91853490A69F08".toByteArray(), str.toByteArray())
    }

    private external fun XGFilmCloseFile(j: Long)
    private external fun XGFilmOpenFile(bArr: ByteArray?): Long
    private external fun XGFilmReadFile(j: Long, j2: Long, i: Int, bArr: ByteArray?): Int
    private external fun dosetupload(i: Int): Int
    private external fun doxSetP2PPauseUpdate(i: Int)
    private external fun doxadd(bArr: ByteArray?): Int
    private external fun doxcheck(bArr: ByteArray?): Int
    private external fun doxdel(bArr: ByteArray?): Int
    private external fun doxdelall(): Int
    private external fun doxdownload(bArr: ByteArray?): Int
    private external fun doxendhttpd(): Int
    private external fun doxgetVersion(): String?
    private external fun doxgethostbynamehook(str: String?): String?
    private external fun doxgetlocalAddress(): String?
    private external fun doxgettaskstat(i: Int): String?
    private external fun doxpause(bArr: ByteArray?): Int
    private external fun doxsave(): Int
    private external fun doxsetduration(i: Int): Int
    private external fun doxstart(bArr: ByteArray?): Int
    private external fun doxstarthttpd(bArr: ByteArray?, bArr2: ByteArray?): Int
    private external fun doxterminate(): Int
    private external fun getdownsize(i: Int): Long
    private external fun getfilesize(i: Int): Long
    private external fun getlocalfilesize(bArr: ByteArray?): Long
    private external fun getpercent(): Int
    private external fun getspeed(i: Int): Long

    fun P2Pdoxstart(bArr: ByteArray?): Int = doxstart(bArr)
    fun P2Pdoxdownload(bArr: ByteArray?): Int = doxdownload(bArr)
    fun P2Pdoxterminate(): Int = doxterminate()
    fun P2Pdosetupload(i: Int): Int = dosetupload(i)
    fun P2Pdoxcheck(bArr: ByteArray?): Int = doxcheck(bArr)
    fun P2Pdoxadd(bArr: ByteArray?): Int = doxadd(bArr)
    fun P2Pdoxpause(bArr: ByteArray?): Int = doxpause(bArr)
    fun P2Pdoxdel(bArr: ByteArray?): Int = doxdel(bArr)
    fun P2PdoxdelAll(): Int = doxdelall()
    fun P2Pgetspeed(i: Int): Long = getspeed(i)
    fun P2Pgetdownsize(i: Int): Long = getdownsize(i)
    fun P2Pgetfilesize(i: Int): Long = getfilesize(i)
    fun P2Pgetpercent(): Int = getpercent()
    fun P2Pgetlocalfilesize(bArr: ByteArray?): Long = getlocalfilesize(bArr)
    fun P2Pdosetduration(i: Int): Long = doxsetduration(i).toLong()
    fun getServiceAddress(): String? = doxgethostbynamehook("xx0.github.com")
    fun P2Pdoxstarthttpd(bArr: ByteArray?, bArr2: ByteArray?): Int = doxstarthttpd(bArr, bArr2)
    fun P2Pdoxsave(): Int = doxsave()
    fun P2Pdoxendhttpd(): Int = doxendhttpd()
    fun getVersion(): String? = doxgetVersion()
    fun xGFilmOpenFile(bArr: ByteArray?): Long = XGFilmOpenFile(bArr)
    fun xGFilmCloseFile(j: Long) = XGFilmCloseFile(j)
    fun xGFilmReadFile(j: Long, j2: Long, i: Int, bArr: ByteArray?): Int = XGFilmReadFile(j, j2, i, bArr)
    fun setP2PPauseUpdate(i: Int) = doxSetP2PPauseUpdate(i)
    fun getTouPingUrl(): String? = doxgetlocalAddress()
    fun P2Pdoxgettaskstat(i: Int): String? = doxgettaskstat(i)

    companion object {
        private const val TAG = "P2PClass"

        @JvmField
        var port = 8087

        init {
            System.loadLibrary("p2p")
        }
    }
}
