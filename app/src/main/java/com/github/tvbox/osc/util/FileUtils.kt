package com.github.tvbox.osc.util

import android.os.Environment
import android.text.TextUtils
import android.util.Base64
import com.github.catvod.net.OkHttp
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.server.ControlManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.orhanobut.hawk.Hawk
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.regex.Pattern

object FileUtils {
    @JvmStatic
    fun writeSimple(data: ByteArray, dst: File): Boolean {
        try {
            if (dst.exists()) dst.delete()
            val bos = BufferedOutputStream(FileOutputStream(dst))
            bos.write(data)
            bos.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun readSimple(src: File): ByteArray? {
        try {
            val bis = BufferedInputStream(FileInputStream(src))
            val len = bis.available()
            val data = ByteArray(len)
            bis.read(data)
            bis.close()
            return data
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(source: File, dest: File) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(source)
            outputStream = FileOutputStream(dest)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    @JvmStatic
    fun recursiveDelete(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) {
                    recursiveDelete(f)
                }
            }
        }
        file.delete()
    }

    @JvmStatic
    fun readFileToString(path: String, charsetName: String): String {
        var jsonString = ""
        var `in`: BufferedReader? = null
        try {
            `in` = BufferedReader(InputStreamReader(FileInputStream(File(path)), charsetName))
            var thisLine: String?
            while (`in`.readLine().also { thisLine = it } != null) {
                jsonString += thisLine
            }
            `in`.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (`in` != null) {
                try {
                    `in`.close()
                } catch (ignored: IOException) {
                }
            }
        }
        return jsonString
    }

    @JvmStatic
    fun getRootPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    @JvmStatic
    fun getLocal(path: String): File {
        return File(path.replace("file:/", getRootPath()))
    }

    @JvmStatic
    fun getCacheDir(): File {
        return App.getInstance().cacheDir
    }

    @JvmStatic
    fun getCachePath(): String {
        return getCacheDir().absolutePath
    }

    @JvmStatic
    fun getFilePath(): String {
        return App.getInstance().filesDir.absolutePath
    }

    @JvmStatic
    fun cleanDirectory(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles()
        if (files == null || files.isEmpty()) return
        for (one in files) {
            try {
                deleteFile(one)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun isWeekAgo(file: File): Boolean {
        val oneWeekMillis = 3L * 24 * 60 * 60 * 1000
        val timeDiff = System.currentTimeMillis() - file.lastModified()
        return timeDiff > oneWeekMillis
    }

    @JvmStatic
    fun deleteFile(file: File) {
        if (!file.exists()) return
        if (file.isFile) {
            if (file.canWrite()) file.delete()
            return
        }
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files == null || files.isEmpty()) {
                if (file.canWrite()) file.delete()
                return
            }
            for (one in files) {
                deleteFile(one)
            }
        }
    }

    @JvmStatic
    fun cleanPlayerCache() {
        val ijkCachePath = getCachePath() + "/ijkcaches/"
        val thunderCachePath = getCachePath() + "/thunder/"
        val ijkCacheDir = File(ijkCachePath)
        val thunderCacheDir = File(thunderCachePath)
        try {
            if (ijkCacheDir.exists()) cleanDirectory(ijkCacheDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (thunderCacheDir.exists()) cleanDirectory(thunderCacheDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun clearSpiderCacheFiles() {
        cleanDirectory(File(getFilePath() + "/csp/"))
        cleanDirectory(File(getCachePath() + "/jar/"))
        cleanDirectory(File(getCachePath() + "/py/"))
        cleanDirectory(File(getCachePath() + "/catvod_jsapi/"))
        clearJsModuleCache()
    }

    private fun clearJsModuleCache() {
        val externalCacheDir = File(getExternalCachePath())
        val files = externalCacheDir.listFiles() ?: return
        for (file in files) {
            if (file != null && file.name.startsWith("qjscache_")) {
                deleteFile(file)
            }
        }
    }

    @JvmStatic
    fun read(path: String): String {
        try {
            val br = BufferedReader(InputStreamReader(FileInputStream(getLocal(path))))
            val sb = StringBuilder()
            var text: String?
            while (br.readLine().also { text = it } != null) sb.append(text).append("\n")
            br.close()
            return sb.toString()
        } catch (e: Exception) {
            return ""
        }
    }

    @JvmStatic
    fun getFileName(filePath: String?): String {
        if (TextUtils.isEmpty(filePath)) return ""
        var fileName = filePath!!
        val p = fileName.lastIndexOf(File.separatorChar)
        if (p != -1) {
            fileName = fileName.substring(p + 1)
        }
        return fileName
    }

    @JvmStatic
    fun getFileNameWithoutExt(filePath: String?): String {
        if (TextUtils.isEmpty(filePath)) return ""
        var fileName = filePath!!
        var p = fileName.lastIndexOf(File.separatorChar)
        if (p != -1) {
            fileName = fileName.substring(p + 1)
        }
        p = fileName.indexOf('.')
        if (p != -1) {
            fileName = fileName.substring(0, p)
        }
        return fileName
    }

    @JvmStatic
    fun hasExtension(path: String): Boolean {
        val lastDotIndex = path.lastIndexOf(".")
        val lastSlashIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"))
        return lastDotIndex > lastSlashIndex && lastDotIndex < path.length - 1
    }

    @JvmStatic
    fun saveCache(cache: File, json: String) {
        try {
            val cacheDir = cache.parentFile
            if (cacheDir != null && !cacheDir.exists()) cacheDir.mkdirs()
            if (cache.exists()) cache.delete()
            val fos = FileOutputStream(cache)
            fos.write(json.toByteArray(charset("UTF-8")))
            fos.flush()
            fos.close()
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    // JS 工具方法
    private val URL_JOIN = Pattern.compile("^http.*\\.(js|txt|json)", Pattern.MULTILINE or Pattern.CASE_INSENSITIVE)

    @JvmStatic
    fun loadModule(nameParam: String): String {
        var name = nameParam
        var rel: String? = null
        try {
            if (name.contains("gbk.js")) {
                name = "gbk.js"
            } else if (name.contains("模板.js")) {
                name = "模板.js"
            } else if (name.contains("cat.js")) {
                name = "cat.js"
            }
            LOG.i("echo-loadModule $name")
            val m = URL_JOIN.matcher(name)
            if (m.find()) {
                if (!Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
                    val cache = getCache(MD5.encode(name))
                    rel = cache
                    if (StringUtils.isEmpty(cache)) {
                        val netStr = get(name)
                        if (!TextUtils.isEmpty(netStr)) {
                            setCache(604800, MD5.encode(name), netStr)
                        }
                        rel = netStr
                    }
                } else {
                    rel = get(name)
                }
            } else if (name.startsWith("assets://")) {
                rel = getAsOpen(name.substring(9))
            } else if (isAsFile(name, "js/lib")) {
                rel = getAsOpen("js/lib/$name")
            } else if (name.startsWith("file://")) {
                rel = get(
                    ControlManager.get()
                        .getAddress(true) + "file/" + name.replace("file:///", "")
                        .replace("file://", "")
                )
            } else if (name.startsWith("clan://localhost/")) {
                rel = get(
                    ControlManager.get()
                        .getAddress(true) + "file/" + name.replace("clan://localhost/", "")
                )
            } else if (name.startsWith("clan://")) {
                val substring = name.substring(7)
                val indexOf = substring.indexOf(47.toChar())
                rel = get("http://" + substring.substring(0, indexOf) + "/file/" + substring.substring(indexOf + 1))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return rel ?: ""
    }

    private val cachedDirFiles: MutableMap<String, Set<String>> = HashMap()

    @JvmStatic
    fun isAsFile(name: String, dir: String): Boolean {
        var files = cachedDirFiles[dir]
        if (files == null) {
            LOG.i("echo-读取AssetsList")
            val list = try {
                App.getInstance().assets.list(dir)
            } catch (e: IOException) {
                null
            }
            val loaded: Set<String> = if (list != null) HashSet(listOf(*list)) else emptySet()
            cachedDirFiles[dir] = loaded
            files = loaded
        }
        return files.contains(name.trim())
    }

    @JvmStatic
    fun getAsOpen(name: String): String {
        try {
            val `is` = App.getInstance().assets.open(name)
            val data = ByteArray(`is`.available())
            `is`.read(data)
            `is`.close()
            return String(data, charset("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @JvmStatic
    fun getCache(name: String): String {
        try {
            var code = ""
            val file = open(name)
            if (file.exists()) {
                val bytes = readSimple(file)
                if (bytes != null) {
                    code = String(bytes)
                }
            }
            if (TextUtils.isEmpty(code)) {
                return ""
            }
            val asJsonObject = Gson().fromJson(code, JsonObject::class.java).asJsonObject
            if (asJsonObject.get("expires").asLong <= System.currentTimeMillis() / 1000) {
                recursiveDelete(open(name))
            }
            return asJsonObject.get("data")?.asString ?: ""
        } catch (e4: Exception) {
            return ""
        }
    }

    @JvmStatic
    fun setCache(time: Int, name: String, data: String) {
        try {
            val jSONObject = JSONObject()
            jSONObject.put("expires", (time + (System.currentTimeMillis() / 1000)).toInt())
            jSONObject.put("data", data)
            writeSimple(jSONObject.toString().toByteArray(), open(name))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun setCacheByte(name: String, data: ByteArray) {
        try {
            writeSimple(byteMerger("//DRPY".toByteArray(), Base64.encode(data, Base64.URL_SAFE)), open("B_$name"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun byteMerger(bt1: ByteArray, bt2: ByteArray): ByteArray {
        val bt3 = ByteArray(bt1.size + bt2.size)
        System.arraycopy(bt1, 0, bt3, 0, bt1.size)
        System.arraycopy(bt2, 0, bt3, bt1.size, bt2.size)
        return bt3
    }

    @JvmStatic
    fun get(str: String): String {
        return get(str, null)
    }

    @JvmStatic
    fun get(str: String, headerMapParam: Map<String, String>?): String {
        var headerMap = headerMapParam
        if (headerMap == null) {
            val map = HashMap<String, String>()
            map["User-Agent"] = if (str.startsWith("https://gitcode.net/")) UA.random() else "okhttp/3.15"
            headerMap = map
        }
        return OkHttp.string(str, headerMap)
    }

    @JvmStatic
    fun open(str: String): File {
        return File(getExternalCachePath() + "/qjscache_" + str + ".js")
    }

    @JvmStatic
    fun getExternalCachePath(): String {
        val externalCacheDir = App.getInstance().externalCacheDir
        return externalCacheDir?.absolutePath ?: getCachePath()
    }
}
