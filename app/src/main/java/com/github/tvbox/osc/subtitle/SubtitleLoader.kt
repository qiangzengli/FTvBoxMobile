package com.github.tvbox.osc.subtitle

import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.github.tvbox.osc.subtitle.exception.FatalParsingException
import com.github.tvbox.osc.subtitle.format.FormatASS
import com.github.tvbox.osc.subtitle.format.FormatSRT
import com.github.tvbox.osc.subtitle.format.FormatSTL
import com.github.tvbox.osc.subtitle.format.TimedTextFileFormat
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.UnicodeReader
import com.lzy.okgo.OkGo
import okhttp3.Response
import org.apache.commons.io.input.ReaderInputStream
import org.mozilla.universalchardet.UniversalDetector
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.URLDecoder
import java.nio.charset.Charset

object SubtitleLoader {
    private val TAG = SubtitleLoader::class.java.simpleName

    @JvmStatic
    fun loadSubtitle(path: String?, callback: Callback?) {
        if (path.isNullOrEmpty()) {
            return
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            loadFromRemoteAsync(path, callback)
        } else if (path.startsWith("data:")) {
            loadFromDataAsync(path, callback)
        } else {
            loadFromLocalAsync(path, callback)
        }
    }

    private fun loadFromDataAsync(dataPath: String, callback: Callback?) {
        AppTaskExecutor.deskIO().execute {
            try {
                val result = loadFromData(dataPath)
                if (callback != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onSuccess(result)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (callback != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onError(e)
                    }
                }
            }
        }
    }

    private fun loadFromRemoteAsync(remoteSubtitlePath: String, callback: Callback?) {
        AppTaskExecutor.deskIO().execute {
            try {
                val subtitleLoadSuccessResult = loadFromRemote(remoteSubtitlePath)
                if (callback != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onSuccess(subtitleLoadSuccessResult)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (callback != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onError(e)
                    }
                }
            }
        }
    }

    private fun loadFromLocalAsync(localSubtitlePath: String, callback: Callback?) {
        AppTaskExecutor.deskIO().execute {
            try {
                val subtitleLoadSuccessResult = loadFromLocal(localSubtitlePath)
                if (callback != null && subtitleLoadSuccessResult != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onSuccess(subtitleLoadSuccessResult)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (callback != null) {
                    AppTaskExecutor.mainThread().execute {
                        callback.onError(e)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun loadSubtitle(path: String?): SubtitleLoadSuccessResult? {
        if (path.isNullOrEmpty()) {
            return null
        }
        try {
            return if (path.startsWith("http://") || path.startsWith("https://")) {
                loadFromRemote(path)
            } else if (path.startsWith("data:")) {
                loadFromData(path)
            } else {
                loadFromLocal(path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(IOException::class, FatalParsingException::class)
    private fun loadFromData(dataPath: String): SubtitleLoadSuccessResult {
        val comma = dataPath.indexOf(',')
        if (comma < 0) throw IOException("Invalid subtitle data URI")
        val fragment = dataPath.indexOf('#', comma + 1)
        val metadata = dataPath.substring(5, comma).lowercase()
        val encoded = dataPath.substring(comma + 1, if (fragment < 0) dataPath.length else fragment)
        val bytes = if (metadata.contains(";base64")) {
            Base64.decode(encoded, Base64.DEFAULT)
        } else {
            Uri.decode(encoded).toByteArray(charset("UTF-8"))
        }
        var fileName = if (fragment < 0) "subtitle.ass" else Uri.decode(dataPath.substring(fragment + 1))
        if (!FileUtils.hasExtension(fileName)) {
            fileName += if (metadata.contains("vtt")) ".vtt" else ".ass"
        }
        val content = String(bytes, charset("UTF-8"))
        val result = SubtitleLoadSuccessResult()
        result.timedTextObject = loadAndParse(ByteArrayInputStream(bytes), fileName)
        result.fileName = fileName
        result.content = content
        result.subtitlePath = dataPath
        return result
    }

    @Throws(Exception::class)
    private fun loadFromRemote(remoteSubtitlePath: String): SubtitleLoadSuccessResult {
        Log.d(TAG, "parseRemote: remoteSubtitlePath = $remoteSubtitlePath")
        var referer = ""
        if (remoteSubtitlePath.contains("alicloud") || remoteSubtitlePath.contains("aliyundrive")) {
            referer = "https://www.aliyundrive.com/"
        } else if (remoteSubtitlePath.contains("assrt.net")) {
            referer = "https://secure.assrt.net/"
        }
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36"
        val response: Response = OkGo.get<String>(remoteSubtitlePath.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            .headers("Referer", referer)
            .headers("User-Agent", ua)
            .execute()
        val body = response.body() ?: throw IOException("Empty response body")
        val bytes = body.bytes()
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        var encoding = detector.detectedCharset
        if (TextUtils.isEmpty(encoding)) encoding = "UTF-8"
        val content = String(bytes, Charset.forName(encoding))
        val `is`: InputStream = ByteArrayInputStream(content.toByteArray())
        var filename = ""
        val contentDisposition = response.header("content-disposition", "") ?: ""
        val cd = contentDisposition.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (cd.size > 1) {
            var filenameInfo = cd[1].trim()
            if (filenameInfo.startsWith("filename=")) {
                filename = filenameInfo.replace("filename=", "").replace("\"", "")
            } else if (filenameInfo.startsWith("filename*=")) {
                filename = filenameInfo.substring(filenameInfo.lastIndexOf("''") + 2)
            }
            filename = URLDecoder.decode(filename.trim())
        }
        var filePath = filename
        if (filePath.isEmpty()) {
            val uri = Uri.parse(remoteSubtitlePath)
            filePath = uri.path ?: ""
        }
        if (!filePath.contains(".") && remoteSubtitlePath.contains("#")) {
            filePath = remoteSubtitlePath.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            filePath = URLDecoder.decode(filePath)
        }
        val subtitleLoadSuccessResult = SubtitleLoadSuccessResult()
        subtitleLoadSuccessResult.timedTextObject = loadAndParse(`is`, filePath)
        subtitleLoadSuccessResult.fileName = filePath
        subtitleLoadSuccessResult.content = content
        subtitleLoadSuccessResult.subtitlePath = remoteSubtitlePath
        return subtitleLoadSuccessResult
    }

    @Throws(IOException::class, FatalParsingException::class)
    private fun loadFromLocal(localSubtitlePath: String): SubtitleLoadSuccessResult? {
        Log.d(TAG, "parseLocal: localSubtitlePath = $localSubtitlePath")
        val file = File(localSubtitlePath)
        if (!file.exists()) {
            Log.d(TAG, "parseLocal: localSubtitlePath = $localSubtitlePath file not exsits")
            return null
        }
        val bytes = FileUtils.readSimple(file) ?: return null
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        val encoding = detector.detectedCharset ?: "UTF-8"
        val content = String(bytes, Charset.forName(encoding))
        val `is`: InputStream = ByteArrayInputStream(content.toByteArray())
        val filePath = file.path
        val subtitleLoadSuccessResult = SubtitleLoadSuccessResult()
        subtitleLoadSuccessResult.timedTextObject = loadAndParse(`is`, filePath)
        val fileName = filePath.substring(filePath.lastIndexOf("/") + 1)
        subtitleLoadSuccessResult.fileName = fileName
        subtitleLoadSuccessResult.subtitlePath = localSubtitlePath
        return subtitleLoadSuccessResult
    }

    @Throws(IOException::class, FatalParsingException::class)
    private fun loadAndParse(`is`: InputStream, filePath: String): TimedTextObject? {
        val fileName = filePath.substring(filePath.lastIndexOf("/") + 1)
        var ext = ""
        if (fileName.lastIndexOf(".") > 0) {
            ext = fileName.substring(fileName.lastIndexOf("."))
        }
        Log.d(TAG, "parse: name = $fileName, ext = $ext")
        val reader: Reader = UnicodeReader(`is`)
        val newInputStream: InputStream = ReaderInputStream(reader, Charset.defaultCharset())
        if (".srt".equals(ext, ignoreCase = true)) {
            return FormatSRT().parseFile(fileName, newInputStream)
        } else if (".ass".equals(ext, ignoreCase = true)) {
            return FormatASS().parseFile(fileName, newInputStream)
        } else if (".stl".equals(ext, ignoreCase = true)) {
            return FormatSTL().parseFile(fileName, newInputStream)
        } else if (".ttml".equals(ext, ignoreCase = true)) {
            return FormatSTL().parseFile(fileName, newInputStream)
        }
        val arr: Array<TimedTextFileFormat> = arrayOf(FormatSRT(), FormatASS(), FormatSTL(), FormatSTL())
        for (oneFormat in arr) {
            try {
                return oneFormat.parseFile(fileName, newInputStream)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    interface Callback {
        fun onSuccess(subtitleLoadSuccessResult: SubtitleLoadSuccessResult)
        fun onError(exception: Exception)
    }
}
