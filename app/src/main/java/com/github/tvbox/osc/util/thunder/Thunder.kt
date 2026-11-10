package com.github.tvbox.osc.util.thunder

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.FileUtils
import com.xunlei.downloadlib.XLDownloadManager
import com.xunlei.downloadlib.XLTaskHelper
import com.xunlei.downloadlib.android.XLUtil
import com.xunlei.downloadlib.parameter.TorrentFileInfo
import com.xunlei.downloadlib.parameter.XLTaskInfo
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Thunder {
    private var cacheRoot = ""
    private var localPath = ""
    private var name = ""
    private var task_url = ""
    private var currentTask = 0L
    private var torrentFileInfoArrayList: ArrayList<TorrentFileInfo>? = null
    private var threadPool: ExecutorService? = null
    private var playList: ArrayList<String>? = null
    private var ed2kList: ArrayList<String>? = null
    private val formats = ArrayList<String>()

    interface ThunderCallback {
        fun status(code: Int, info: String)
        fun list(urlMap: Map<Int, String>)
        fun play(url: String)
    }

    private fun init(context: Context) {
        val sharedPreferences = context.getSharedPreferences("rand_thunder_id", Context.MODE_PRIVATE)
        var imei = sharedPreferences.getString("imei", null)
        var mac = sharedPreferences.getString("mac", null)
        if (imei == null) {
            imei = randomImei()
            sharedPreferences.edit().putString("imei", imei).commit()
        }
        if (mac == null) {
            mac = randomMac()
            sharedPreferences.edit().putString("mac", mac).commit()
        }
        XLUtil.mIMEI = imei
        XLUtil.isGetIMEI = true
        XLUtil.mMAC = mac
        XLUtil.isGetMAC = true
        val cd3 = "cee25055f125a2fde0"
        val base64Decode = "axzNjAwMQ^^yb==0^852^083dbcff^"
        val substring = base64Decode.substring(1)
        val substring2 = cd3.substring(0, cd3.length - 1)
        val cd = substring + substring2
        XLTaskHelper.init(context, cd, "21.01.07.800002")
        cacheRoot = context.cacheDir.absolutePath + File.separator + "thunder"
    }

    @JvmStatic
    fun stop(bool: Boolean) {
        if (currentTask > 0) {
            XLTaskHelper.instance().stopTask(currentTask)
            currentTask = 0L
        }
        if (bool) {
            torrentFileInfoArrayList = null
            val cache = File(if (task_url.isEmpty()) cacheRoot else localPath)
            recursiveDelete(cache)
            if (!cache.exists()) cache.mkdirs()
            if (threadPool != null) {
                try {
                    threadPool?.shutdownNow()
                    threadPool = null
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    @JvmStatic
    fun parse(context: Context, urlBean: Movie.Video.UrlBean, callback: ThunderCallback) {
        init(context)
        stop(true)
        threadPool = Executors.newSingleThreadExecutor()
        torrentFileInfoArrayList = ArrayList()
        playList = ArrayList()
        ed2kList = ArrayList()
        val urlMap: MutableMap<Int, String> = HashMap()
        threadPool?.execute {
            val infoList = urlBean.infoList ?: return@execute
            for (idx in infoList.indices) {
                val urlInfo = infoList[idx]
                val beanList = urlInfo?.beanList
                if (beanList != null) {
                    var url = ""
                    for (infoBean in beanList) {
                        var isParse = false
                        url = infoBean.url ?: ""
                        if (isMagnet(url) || isThunder(url) || isTorrent(url)) {
                            if (isThunder(url)) url = XLDownloadManager.getInstance().parserThunderUrl(url)
                            val link = if (isThunder(url)) XLDownloadManager.getInstance().parserThunderUrl(url) else url
                            val p = Uri.parse(link) ?: continue
                            val fileName = XLTaskHelper.instance().getFileName(link)
                            val cache = File(cacheRoot + File.separator + fileName)
                            try {
                                if (currentTask > 0) {
                                    XLTaskHelper.instance().stopTask(currentTask)
                                    currentTask = 0L
                                }
                                currentTask = if (isMagnet(url)) {
                                    XLTaskHelper.instance().addMagentTask(url, cacheRoot, fileName)
                                } else {
                                    XLTaskHelper.instance().addThunderTask(url, cacheRoot, fileName)
                                }
                            } catch (exception: Exception) {
                                exception.printStackTrace()
                                currentTask = 0
                            }
                            if (currentTask <= 0) {
                                continue
                            }
                            var count = 30
                            outerLoop@ while (true) {
                                count--
                                if (count <= 0) break
                                val taskInfo = XLTaskHelper.instance().getTaskInfo(currentTask)
                                if (taskInfo != null) {
                                    when (taskInfo.mTaskStatus) {
                                        2 -> {
                                            try {
                                                val torrentInfo = XLTaskHelper.instance().getTorrentInfo(cache.absolutePath)
                                                if (torrentInfo != null && !TextUtils.isEmpty(torrentInfo.mInfoHash)) {
                                                    val mSubFileInfo = torrentInfo.mSubFileInfo
                                                    if (mSubFileInfo != null) {
                                                        for (sub in mSubFileInfo) {
                                                            if (isMedia(sub.mFileName) && sub.mFileSize > 1048576L * 30) {
                                                                sub.torrentPath = cache.absolutePath
                                                                playList?.add(sub.mFileName + "\$tvbox-torrent:" + (torrentFileInfoArrayList?.size ?: 0))
                                                                torrentFileInfoArrayList?.add(sub)
                                                            }
                                                        }
                                                        isParse = true
                                                        break@outerLoop
                                                    }
                                                }
                                            } catch (throwable: Throwable) {
                                                throwable.printStackTrace()
                                            }
                                        }
                                        3 -> break@outerLoop
                                    }
                                }
                                try {
                                    Thread.sleep(100)
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            url = infoBean.url ?: ""
                            if (isThunder(url)) url = XLDownloadManager.getInstance().parserThunderUrl(url)
                            if (isNetworkDownloadTask(url)) {
                                task_url = url
                                if (TextUtils.isEmpty(task_url)) continue
                                name = XLTaskHelper.instance().getFileName(task_url)
                                playList?.add(name + "\$tvbox-oth:" + (ed2kList?.size ?: 0))
                                ed2kList?.add(task_url)
                                isParse = true
                            }
                        }
                        if (!isParse) playList?.add((infoBean.name ?: "") + "$" + infoBean.url)
                    }
                    val currentPlayList = playList
                    if (currentPlayList != null && currentPlayList.isNotEmpty()) {
                        urlMap[idx] = TextUtils.join("#", currentPlayList)
                        currentPlayList.clear()
                    }
                }
            }
            if (urlMap.isNotEmpty()) {
                callback.list(urlMap)
            } else {
                callback.status(-1, "解析异常")
            }
        }
    }

    @JvmStatic
    fun play(url: String, callback: ThunderCallback): Boolean {
        if (url.startsWith("tvbox-torrent:")) {
            val idx = url.substring(14).toInt()
            val info = torrentFileInfoArrayList?.get(idx) ?: return false
            if (currentTask > 0) {
                XLTaskHelper.instance().stopTask(currentTask)
                currentTask = 0L
            }
            threadPool?.execute {
                val torrentName = File(info.torrentPath).name
                val cache = cacheRoot + File.separator + torrentName.substring(0, torrentName.lastIndexOf("."))
                currentTask = XLTaskHelper.instance().addTorrentTask(info.torrentPath, cache, info.mFileIndex)
                if (currentTask < 0) callback.status(-1, "下载出错")
                var count = 30
                while (true) {
                    count--
                    if (count <= 0) {
                        callback.status(-1, "解析下载超时")
                        break
                    }
                    val taskInfo = XLTaskHelper.instance().getBtSubTaskInfo(currentTask, info.mFileIndex).mTaskInfo
                    when (taskInfo.mTaskStatus) {
                        3 -> {
                            callback.status(-1, errorInfo(taskInfo.mErrorCode))
                            return@execute
                        }
                        1, 4, 2 -> {
                            val pUrl = XLTaskHelper.instance().getLoclUrl(cache + File.separator + info.mFileName)
                            callback.play(pUrl)
                            return@execute
                        }
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            return true
        }
        if (url.startsWith("tvbox-oth:")) {
            stop(false)
            val idx = url.substring(10).toInt()
            task_url = ed2kList?.get(idx) ?: return false
            name = XLTaskHelper.instance().getFileName(task_url)
            localPath = File(cacheRoot + File.separator + "temp", FileUtils.getFileNameWithoutExt(name)).toString() + "/"
            currentTask = XLTaskHelper.instance().addThunderTask(task_url, localPath, null)
            threadPool?.execute {
                var count = 20
                while (true) {
                    count--
                    if (count <= 0) {
                        callback.status(-1, "解析下载超时")
                        break
                    }
                    val playUrl = getPlayUrl()
                    if (!playUrl.isNullOrEmpty()) {
                        callback.play(playUrl)
                        return@execute
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            return true
        }
        if (isEd2k(url) || isFtp(url)) {
            if (threadPool == null) {
                init(App.getInstance())
                threadPool = Executors.newSingleThreadExecutor()
            }
            if (currentTask > 0) {
                XLTaskHelper.instance().stopTask(currentTask)
                currentTask = 0L
            }
            task_url = url
            name = XLTaskHelper.instance().getFileName(task_url)
            localPath = File(cacheRoot + File.separator + "temp", FileUtils.getFileNameWithoutExt(name)).toString() + "/"
            currentTask = XLTaskHelper.instance().addThunderTask(task_url, localPath, null)
            threadPool?.execute {
                var count = 20
                while (true) {
                    count--
                    if (count <= 0) {
                        callback.status(-1, "解析下载超时")
                        break
                    }
                    val playUrl = getPlayUrl()
                    if (!TextUtils.isEmpty(playUrl)) {
                        callback.play(playUrl!!)
                        return@execute
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            return true
        }
        return false
    }

    private fun errorInfo(code: Int): String {
        return when (code) {
            9125 -> "文件名太长"
            111120 -> "文件路径太长"
            111142 -> "文件太小"
            111085 -> "磁盘空间不足"
            111171 -> "拒绝的网络连接"
            9301 -> "缓冲区不足"
            114001, 114004, 114005, 114006, 114007, 114011, 9304, 111154 -> "版权限制：无权下载"
            114101 -> "无效链接"
            else -> "ErrorCode=$code"
        }
    }

    @JvmStatic
    fun isSupportUrl(url: String): Boolean {
        return isMagnet(url) || isThunder(url) || isTorrent(url)
    }

    private fun isMagnet(url: String): Boolean {
        return url.lowercase().startsWith("magnet:")
    }

    private fun isThunder(url: String): Boolean {
        return url.lowercase().startsWith("thunder")
    }

    private fun isTorrent(url: String): Boolean {
        return url.lowercase().split(";".toRegex()).toTypedArray()[0].endsWith(".torrent")
    }

    private fun isEd2k(url: String): Boolean {
        return url.lowercase().startsWith("ed2k:")
    }

    @JvmStatic
    fun isFtp(url: String): Boolean {
        return url.lowercase().startsWith("ftp://")
    }

    private fun recursiveDelete(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) recursiveDelete(f)
            }
        }
        file.delete()
    }

    private fun isMedia(name: String?): Boolean {
        if (name == null) return false
        if (formats.isEmpty()) {
            formats.add(".rmvb")
            formats.add(".avi")
            formats.add(".mkv")
            formats.add(".flv")
            formats.add(".mp4")
            formats.add(".rm")
            formats.add(".vob")
            formats.add(".wmv")
            formats.add(".mov")
            formats.add(".3gp")
            formats.add(".asf")
            formats.add("mpg")
            formats.add("mpeg")
            formats.add("mpe")
        }
        val lower = name.lowercase()
        for (f in formats) {
            if (lower.endsWith(f)) return true
        }
        return false
    }

    private fun randomImei(): String {
        return randomString("0123456", 15)
    }

    private fun randomMac(): String {
        return randomString("ABCDEF0123456", 12).uppercase()
    }

    private fun randomString(base: String, length: Int): String {
        val random = Random()
        val sb = StringBuilder()
        for (i in 0 until length) {
            val number = random.nextInt(base.length)
            sb.append(base[number])
        }
        return sb.toString()
    }

    @JvmStatic
    fun isNetworkDownloadTask(url: String?): Boolean {
        if (TextUtils.isEmpty(url)) return false
        return isFtp(url!!) || isEd2k(url)
    }

    @JvmStatic
    fun stopTask() {
        if (currentTask != 0L) {
            XLTaskHelper.instance().deleteTask(currentTask, if (task_url.isEmpty()) cacheRoot else localPath)
            currentTask = 0L
        }
    }

    @JvmStatic
    fun getTaskInfo(): XLTaskInfo? {
        return XLTaskHelper.instance().getTaskInfo(currentTask)
    }

    @JvmStatic
    fun getPlayUrl(): String? {
        if (currentTask != 0L) {
            if (isNetworkDownloadTask(task_url)) {
                return XLTaskHelper.instance().getLoclUrl(localPath + name)
            }
        }
        return null
    }
}
