package com.github.tvbox.osc.server

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.receiver.PushReceiver
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.Proxy
import com.github.tvbox.osc.util.RegexUtils.getPattern
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import org.greenrobot.eventbus.EventBus
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Comparator
import java.util.Enumeration
import java.util.HashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
class RemoteServer(port: Int, private val mContext: Context) : NanoHTTPD(port) {
    private var isStarted = false
    private var mDataReceiver: DataReceiver? = null
    private val getRequestList = ArrayList<RequestProcess>()
    private val postRequestList = ArrayList<RequestProcess>()

    init {
        addGetRequestProcess()
        addPostRequestProcess()
    }

    private fun addGetRequestProcess() {
        getRequestList.add(RawRequestProcess(this.mContext, "/", R.raw.index, MIME_HTML))
        getRequestList.add(RawRequestProcess(this.mContext, "/index.html", R.raw.index, MIME_HTML))
        getRequestList.add(RawRequestProcess(this.mContext, "/style.css", R.raw.style, "text/css"))
        getRequestList.add(RawRequestProcess(this.mContext, "/ui.css", R.raw.ui, "text/css"))
        getRequestList.add(RawRequestProcess(this.mContext, "/jquery.js", R.raw.jquery, "application/x-javascript"))
        getRequestList.add(RawRequestProcess(this.mContext, "/script.js", R.raw.script, "application/x-javascript"))
        getRequestList.add(RawRequestProcess(this.mContext, "/favicon.ico", R.drawable.app_icon, "image/x-icon"))
        getRequestList.add(CacheRequestProcess())
    }

    private fun addPostRequestProcess() {
        postRequestList.add(InputRequestProcess(this))
        postRequestList.add(CacheRequestProcess())
    }

    @Throws(IOException::class)
    override fun start(timeout: Int, daemon: Boolean) {
        isStarted = true
        super.start(timeout, daemon)
        EventBus.getDefault().post(ServerEvent(ServerEvent.SERVER_SUCCESS))
    }

    override fun stop() {
        super.stop()
        isStarted = false
    }

    @Suppress("UNCHECKED_CAST")
    private fun getProxy(rs: Array<Any?>?): Response {
        try {
            if (rs == null || rs.size < 3) {
                LOG.e("echo-proxy error: empty proxy result")
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500")
            }
            if (rs[0] is Response) return rs[0] as Response
            val code = rs[0] as Int
            val mime = rs[1] as String
            val stream = if (rs[2] != null) rs[2] as InputStream else null
            val response = newChunkedResponse(
                Response.Status.lookup(code),
                mime,
                stream
            )
            // 添加头部信息
            if (rs.size >= 4 && rs[3] is Map<*, *>) {
                val mapHeader = rs[3] as Map<String, String>
                if (mapHeader.isNotEmpty()) {
                    for (key in mapHeader.keys) {
                        response.addHeader(key, mapHeader[key])
                    }
                }
            }
            return response
        } catch (th: Throwable) {
            LOG.e("echo-proxy error: " + th.message)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500")
        }
    }

    override fun serve(session: IHTTPSession): Response {
        EventBus.getDefault().post(ServerEvent(ServerEvent.SERVER_CONNECTION))
        if (session.uri.isNotEmpty()) {
            var fileName = session.uri.trim()
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'))
            }
            if (session.method == Method.GET) {
                if (isProxyRequest(fileName, session.parms)) {
                    return handleProxy(session)
                }
                for (process in getRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.parms, null)
                    }
                }
                if (fileName.startsWith("/file/")) {
                    try {
                        val f = fileName.substring(6)
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        val file = "$root/$f"
                        val localFile = File(file)
                        return if (localFile.exists()) {
                            if (localFile.isFile) {
                                newChunkedResponse(Response.Status.OK, "application/octet-stream", FileInputStream(localFile))
                            } else {
                                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fileList(root, f))
                            }
                        } else {
                            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "File $file not found!")
                        }
                    } catch (th: Throwable) {
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, th.message)
                    }
                } else if (fileName == "/dns-query") {
                    val name = session.parms["name"]
                    var rs: ByteArray?
                    try {
                        rs = OkGoHelper.dnsOverHttps?.lookupHttpsForwardSync(name ?: "")
                    } catch (th: Throwable) {
                        rs = ByteArray(0)
                    }
                    if (rs == null) rs = ByteArray(0)
                    return newFixedLengthResponse(Response.Status.OK, "application/dns-message", ByteArrayInputStream(rs), rs.size.toLong())
                } else if (fileName.startsWith("/push/")) {
                    var url = fileName.substring(6)
                    if (url.startsWith("b64:")) {
                        try {
                            url = String(Base64.decode(url.substring(4), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), charset("UTF-8"))
                        } catch (e: UnsupportedEncodingException) {
                            e.printStackTrace()
                        }
                    } else {
                        url = URLDecoder.decode(url)
                    }
                    PushReceiver.send(mContext, url)
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
                } else if (fileName == "/action") {
                    return handleAction(session.parms)
                } else if (fileName == "/media") {
                    return handleMedia()
                } else if (fileName.startsWith("/proxyM3u8")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", if (m3u8Content == null) "" else m3u8Content)
                } else if (fileName.startsWith("/dash/")) {
                    val dashData = App.getInstance().dashData ?: ""
                    try {
                        val data = String(Base64.decode(dashData, Base64.DEFAULT or Base64.NO_WRAP), charset("UTF-8"))
                        return newFixedLengthResponse(
                            Response.Status.OK,
                            "application/dash+xml",
                            data
                        )
                    } catch (th: Throwable) {
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, dashData)
                    }
                }
            } else if (session.method == Method.POST) {
                val files = HashMap<String, String>()
                try {
                    if (session.headers.containsKey("content-type")) {
                        val hd = session.headers["content-type"]
                        if (hd != null) {
                            if (hd.toLowerCase().contains("multipart/form-data") && !hd.toLowerCase().contains("charset=")) {
                                val matcher = getPattern("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd)
                                val boundary = if (matcher.find()) matcher.group(1) else null
                                if (boundary != null) {
                                    session.headers["content-type"] = "multipart/form-data; charset=utf-8; $boundary"
                                }
                            }
                        }
                    }
                    session.parseBody(files)
                } catch (ioExc: IOException) {
                    return createPlainTextResponse(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioExc.message)
                } catch (rex: ResponseException) {
                    return createPlainTextResponse(rex.status, rex.message ?: "")
                }
                for (process in postRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.parms, files)
                    }
                }
                try {
                    val params = session.parms
                    if (fileName == "/upload") {
                        val path = params["path"]
                        for (k in files.keys) {
                            if (k.startsWith("files-")) {
                                val fn = params[k]
                                val tmpFile = files[k]
                                val tmp = File(tmpFile)
                                val root = Environment.getExternalStorageDirectory().absolutePath
                                val file = File("$root/$path/$fn")
                                if (file.exists()) file.delete()
                                if (tmp.exists()) {
                                    if (fn != null && fn.toLowerCase().endsWith(".zip")) {
                                        unzip(tmp, "$root/$path")
                                    } else {
                                        FileUtils.copyFile(tmp, file)
                                    }
                                }
                                if (tmp.exists()) tmp.delete()
                            }
                        }
                        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                    } else if (fileName == "/newFolder") {
                        val path = params["path"]
                        val name = params["name"]
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        val file = File("$root/$path/$name")
                        if (!file.exists()) {
                            file.mkdirs()
                            val flag = File("$root/$path/$name/.tvbox_folder")
                            if (!flag.exists()) flag.createNewFile()
                        }
                        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                    } else if (fileName == "/delFolder") {
                        val path = params["path"]
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        val file = File("$root/$path")
                        if (file.exists()) {
                            FileUtils.recursiveDelete(file)
                        }
                        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                    } else if (fileName == "/delFile") {
                        val path = params["path"]
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        val file = File("$root/$path")
                        if (file.exists()) {
                            file.delete()
                        }
                        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                    }
                } catch (th: Throwable) {
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                }
            }
        }
        // default page: index.html
        return getRequestList[0].doResponse(session, "", null, null)
    }

    private fun isProxyRequest(fileName: String, params: Map<String, String>?): Boolean {
        if (params == null) return false
        if (!params.containsKey("do") && !params.containsKey("go")) return false
        return fileName == "/proxy" || fileName == "/"
    }

    private fun handleProxy(session: IHTTPSession): Response {
        val params = HashMap(session.parms)
        params.putAll(session.headers)
        if (params.containsKey("do")) {
            val isDanmuProxy = "danmu" == params["do"]
            if (isDanmuProxy) normalizeDanmuParams(params)
            if (isDanmuProxy) LOG.i("echo-proxy-danmu params: $params")
            val rs = ApiConfig.get().proxyLocal(params)
            return getProxy(rs)
        }
        if (params.containsKey("go")) {
            val rs = Proxy.proxy(params)
            return getProxy(rs)
        }
        return getProxy(null)
    }

    private fun handleAction(params: Map<String, String>?): Response {
        if (params == null) return createPlainTextResponse(Response.Status.OK, "ok")
        val action = params["do"]
        if ("refresh" == action) {
            handleRefreshAction(params)
        }
        return createPlainTextResponse(Response.Status.OK, "ok")
    }

    private fun handleRefreshAction(params: Map<String, String>) {
        val type = params["type"]
        if ("danmaku" == type) {
            val path = params["path"]
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_DANMU_REFRESH, path ?: ""))
        }
    }

    private fun handleMedia(): Response {
        try {
            val activity = App.getInstance().currentActivity
                ?: return createJSONResponse(Response.Status.OK, "{}")
            val latch = CountDownLatch(1)
            val result = arrayOf(JsonObject())
            val act = activity
            Handler(Looper.getMainLooper()).post {
                try {
                    if (act is com.github.tvbox.osc.ui.activity.DetailActivity) {
                        val playFragment = act.playFragment
                        if (playFragment != null) result[0] = playFragment.mediaInfo
                    }
                } catch (th: Throwable) {
                    LOG.e("echo-media handleMedia error: " + th.message)
                } finally {
                    latch.countDown()
                }
            }
            latch.await(2, TimeUnit.SECONDS)
            return createJSONResponse(Response.Status.OK, result[0].toString())
        } catch (th: Throwable) {
            LOG.e("echo-media error: " + th.message)
            return createJSONResponse(Response.Status.OK, "{}")
        }
    }

    private fun normalizeDanmuParams(params: MutableMap<String, String>) {
        try {
            val vodInfo = App.getInstance().vodInfo ?: return
            if (!TextUtils.isEmpty(vodInfo.name)) params["vodName"] = vodInfo.name ?: ""
            if (!isNumeric(params["vodIndex"])) {
                val episode = getCurrentEpisodeIndex(vodInfo)
                if (!TextUtils.isEmpty(episode)) params["vodIndex"] = episode
            }
        } catch (th: Throwable) {
            LOG.e("echo-proxy-danmu normalize error: " + th.message)
        }
    }

    private fun getCurrentEpisodeIndex(vodInfo: VodInfo): String {
        val seriesMap = vodInfo.seriesMap
        if (seriesMap != null && !TextUtils.isEmpty(vodInfo.playFlag)) {
            val series = seriesMap[vodInfo.playFlag]
            if (series != null && vodInfo.playIndex >= 0 && vodInfo.playIndex < series.size) {
                val current = series[vodInfo.playIndex]
                val cName = current.name
                if (current != null && !TextUtils.isEmpty(cName)) {
                    val number = extractNumber(cName)
                    return if (TextUtils.isEmpty(number)) (cName ?: "") else number
                }
            }
        }
        return (Math.max(0, vodInfo.playIndex) + 1).toString()
    }

    private fun isNumeric(text: String?): Boolean {
        return !TextUtils.isEmpty(text) && text!!.matches("\\d+".toRegex())
    }

    private fun extractNumber(text: String?): String {
        if (TextUtils.isEmpty(text)) return ""
        val matcher = getPattern("\\d+").matcher(text!!)
        return if (matcher.find()) matcher.group() else ""
    }

    var dataReceiver: DataReceiver?
        get() = mDataReceiver
        set(receiver) {
            mDataReceiver = receiver
        }

    fun isStarting(): Boolean {
        return isStarted
    }

    val serverAddress: String
        get() {
            val ipAddress = getLocalIPAddress(mContext)
            return "http://$ipAddress:$serverPort/"
        }

    val loadAddress: String
        get() = "http://127.0.0.1:$serverPort/"

    private fun fileTime(time: Long, fmt: String): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        val date = calendar.time
        val sdf = SimpleDateFormat(fmt)
        return sdf.format(date)
    }

    private fun fileList(root: String, path: String): String {
        val file = File("$root/$path")
        val list = file.listFiles()
        val info = JsonObject()
        info.addProperty("remote", serverAddress.replace("http://", "clan://"))
        info.addProperty("del", 0)
        if (path.isEmpty()) {
            info.addProperty("parent", ".")
        } else {
            info.addProperty("parent", file.parentFile?.absolutePath?.replace("$root/", "")?.replace(root, "") ?: ".")
        }
        if (list == null || list.isEmpty()) {
            info.add("files", JsonArray())
            return info.toString()
        }
        Arrays.sort(list, object : Comparator<File> {
            override fun compare(o1: File, o2: File): Int {
                if (o1.isDirectory && o2.isFile) return -1
                return if (o1.isFile && o2.isDirectory) 1 else o1.name.compareTo(o2.name)
            }
        })
        val result = JsonArray()
        for (f in list) {
            if (f.name.startsWith(".")) {
                if (f.name == ".tvbox_folder") {
                    info.addProperty("del", 1)
                }
                continue
            }
            val fileObj = JsonObject()
            fileObj.addProperty("name", f.name)
            fileObj.addProperty("path", f.absolutePath.replace("$root/", ""))
            fileObj.addProperty("time", fileTime(f.lastModified(), "yyyy/MM/dd aHH:mm:ss"))
            fileObj.addProperty("dir", if (f.isDirectory) 1 else 0)
            result.add(fileObj)
        }
        info.add("files", result)
        return info.toString()
    }

    @Throws(Throwable::class)
    fun unzip(zipFilePath: File, destDirectory: String) {
        val destDir = File(destDirectory)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val zip = ZipFile(zipFilePath)
        val iter = zip.entries()
        while (iter.hasMoreElements()) {
            val entry = iter.nextElement()
            val `is` = zip.getInputStream(entry)
            val filePath = destDirectory + File.separator + entry.name
            if (!entry.isDirectory) {
                extractFile(`is`, filePath)
            } else {
                val dir = File(filePath)
                if (!dir.exists()) dir.mkdirs()
                val flag = File("$dir/.tvbox_folder")
                if (!flag.exists()) flag.createNewFile()
            }
        }
    }

    @Throws(Throwable::class)
    fun extractFile(inputStream: InputStream, destFilePath: String) {
        val dst = File(destFilePath)
        if (dst.exists()) dst.delete()
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(2048)
        var len = inputStream.read(bytesIn)
        while (len > 0) {
            bos.write(bytesIn, 0, len)
            len = inputStream.read(bytesIn)
        }
        bos.close()
    }

    companion object {
        @JvmField
        var serverPort = 9978

        @JvmField
        var m3u8Content: String? = null

        @JvmStatic
        fun createPlainTextResponse(status: Response.IStatus, text: String): Response {
            return newFixedLengthResponse(status, MIME_PLAINTEXT, text)
        }

        @JvmStatic
        fun createJSONResponse(status: Response.IStatus, text: String): Response {
            return newFixedLengthResponse(status, "application/json", text)
        }

        @SuppressLint("DefaultLocale")
        @JvmStatic
        fun getLocalIPAddress(context: Context): String {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipAddress == 0) {
                try {
                    val enumerationNi = NetworkInterface.getNetworkInterfaces()
                    while (enumerationNi.hasMoreElements()) {
                        val networkInterface = enumerationNi.nextElement()
                        val interfaceName = networkInterface.displayName
                        if (interfaceName == "eth0" || interfaceName == "wlan0") {
                            val enumIpAddr = networkInterface.inetAddresses
                            while (enumIpAddr.hasMoreElements()) {
                                val inetAddress = enumIpAddr.nextElement()
                                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                    return inetAddress.hostAddress ?: "0.0.0.0"
                                }
                            }
                        }
                    }
                } catch (e: SocketException) {
                    e.printStackTrace()
                }
            } else {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
            return "0.0.0.0"
        }
    }
}
