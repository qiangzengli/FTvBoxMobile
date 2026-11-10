package com.github.tvbox.osc.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import org.xwalk.core.XWalkInitializer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object XWalkUtils {
    private var xWalkInitializer: XWalkInitializer? = null

    interface XWalkState {
        fun success()
        fun fail()
        fun ignore()
    }

    @JvmStatic
    fun downUrl(): String {
        return String.format("http://home.jundie.top:81/xwalk/maven2/crosswalk-apks-23.53.589.4-%s.zip", getRuntimeAbi())
    }

    @JvmStatic
    fun saveZipFile(): String {
        return String.format("crosswalk-apks-23.53.589.4-%s.zip", getRuntimeAbi())
    }

    @JvmStatic
    fun xWalkLibExist(context: Context): Boolean {
        val libFiles = arrayOf(
            "classes.dex",
            "icudtl.dat",
            "libxwalkcore.so",
            "xwalk.pak",
            "xwalk_100_percent.pak"
        )
        val dir = libExtractPath(context)
        for (lib in libFiles) {
            if (!File("$dir/$lib").exists()) return false
        }
        return true
    }

    @JvmStatic
    fun tryUseXWalk(context: Context, state: XWalkState?) {
        if (!xWalkLibExist(context)) {
            state?.ignore()
            return
        }
        if (xWalkInitializer == null) {
            xWalkInitializer = XWalkInitializer(object : XWalkInitializer.XWalkInitListener {
                override fun onXWalkInitStarted() {}
                override fun onXWalkInitCancelled() {}
                override fun onXWalkInitFailed() {
                    state?.fail()
                }

                override fun onXWalkInitCompleted() {
                    state?.success()
                }
            }, context)
        }
        val initializer = xWalkInitializer
        if (initializer != null && initializer.isXWalkReady) {
            state?.success()
        } else {
            initializer?.initAsync()
        }
    }

    private fun checkEmbedded(activity: Activity): Boolean {
        try {
            val clazz = Class.forName("org.xwalk.core.XWalkCoreWrapper")
            if (clazz != null) {
                val constructor = clazz.getDeclaredConstructor(Context::class.java, Int::class.javaPrimitiveType)
                constructor.isAccessible = true
                val obj = constructor.newInstance(activity, -1)
                val fe = clazz.getDeclaredMethod("findEmbeddedCore")
                fe.isAccessible = true
                if (!(fe.invoke(obj) as Boolean)) {
                    return false
                }
            } else {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    @JvmStatic
    fun apkHadDown(context: Context): Boolean {
        return File(apkPath(context)).exists()
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun unzipXWalkZip(context: Context, archive: String) {
        var bi: BufferedInputStream
        val zf = ZipFile(archive)
        val e = zf.entries()
        while (e.hasMoreElements()) {
            val ze2 = e.nextElement()
            val entryName = ze2.name
            if (ze2.isDirectory) {
                continue
            } else {
                val fileName = entryName.substring(entryName.lastIndexOf("/") + 1)
                if (fileName == "XWalkRuntimeLib.apk") {
                    val tempFile = apkPath(context) + ".tmp"
                    val finalApk = apkPath(context)
                    val f = File(tempFile)
                    if (f.exists()) f.delete()
                    val fFinal = File(finalApk)
                    if (fFinal.exists()) fFinal.delete()
                    val bos = BufferedOutputStream(FileOutputStream(tempFile))
                    bi = BufferedInputStream(zf.getInputStream(ze2))
                    val readContent = byteArr(1024)
                    var readCount = bi.read(readContent)
                    while (readCount != -1) {
                        bos.write(readContent, 0, readCount)
                        readCount = bi.read(readContent)
                    }
                    bos.flush()
                    bos.close()
                    if (File(tempFile).renameTo(File(finalApk))) {
                        LOG.i(finalApk)
                    }
                }
            }
        }
        zf.close()
        val zipFile = File(archive)
        if (zipFile.exists() && zipFile.name.endsWith(".zip")) {
            zipFile.delete()
        }
    }

    private fun byteArr(size: Int): ByteArray = ByteArray(size)

    @JvmStatic
    fun getRuntimeAbi(): String {
        var result = "arm"
        try {
            val cls = Class.forName("org.xwalk.core.XWalkEnvironment")
            val method = cls.getMethod("getRuntimeAbi")
            val obj = method.invoke(null) as? String
            when (obj) {
                "arm64-v8a" -> result = "arm64"
                "x86" -> result = "x86"
                "x86_64" -> result = "x86_64"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun extractXWalkLib(context: Context): Boolean {
        val cls = Class.forName("org.xwalk.core.XWalkDecompressor")
        val method = cls.getMethod("extractResource", String::class.java, String::class.java)
        val obj = method.invoke(null, apkPath(context), libExtractPath(context)) as Boolean
        if (obj) {
            Toast.makeText(context, "解压XWalkView运行组件完成!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "解压XWalkView运行组件失败!", Toast.LENGTH_LONG).show()
        }
        return obj
    }

    @JvmStatic
    fun apkPath(context: Context): String {
        return context.cacheDir.absolutePath + "/XWalkRuntimeLib.apk"
    }

    @JvmStatic
    fun libExtractPath(context: Context): String {
        return context.getDir("extracted_xwalkcore", Context.MODE_PRIVATE).absolutePath
    }
}
