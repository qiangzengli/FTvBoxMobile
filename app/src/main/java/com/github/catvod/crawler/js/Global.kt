package com.github.catvod.crawler.js

import androidx.annotation.Keep
import com.github.catvod.Proxy
import com.github.catvod.crawler.js.rsa.RSAEncrypt
import com.whl.quickjs.wrapper.ContextSetter
import com.whl.quickjs.wrapper.Function
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.JSUtils
import com.whl.quickjs.wrapper.QuickJSContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService

class Global(
    @JvmField
    var executor: ExecutorService
) {
    private var runtime: QuickJSContext? = null
    private val timer: Timer = Timer()

    @Keep
    @Function
    fun getProxy(local: Boolean): String {
        return Proxy.getUrl(local) + "?do=js"
    }

    @Keep
    @Function
    fun js2Proxy(dynamic: Boolean?, siteType: Int?, siteKey: String?, url: String?, headers: JSObject): String {
        val local = dynamic == null || !dynamic
        return (getProxy(local) + "&from=catvod" + "&siteType=" + siteType + "&siteKey=" + siteKey
                + "&header=" + URLEncoder.encode(headers.stringify()) + "&url=" + URLEncoder.encode(url))
    }

    @Keep
    @Function
    fun joinUrl(parent: String?, child: String): String {
        return HtmlParser.joinUrl(parent, child)
    }

    @Keep
    @Function
    fun pd(html: String, rule: String, add_url: String): String {
        return HtmlParser.parseDomForUrl(html, rule, add_url)
    }

    @Keep
    @Function
    fun pdfh(html: String, rule: String): String {
        return HtmlParser.parseDomForUrl(html, rule, "")
    }

    @Keep
    @Function
    fun pdfa(html: String, rule: String): JSArray? {
        val rt = runtime ?: return null
        return JSUtils<String>().toArray(rt, HtmlParser.parseDomForArray(html, rule))
    }

    @Keep
    @Function
    fun pdfla(html: String, p1: String, list_text: String, list_url: String, add_url: String): JSArray? {
        val rt = runtime ?: return null
        return JSUtils<String>().toArray(rt, HtmlParser.parseDomForList(html, p1, list_text, list_url, add_url))
    }

    @Keep
    @Function
    fun s2t(text: String?): String {
        return try {
            Trans.s2t(false, text)
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun t2s(text: String?): String {
        return try {
            Trans.t2s(false, text)
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun aesX(mode: String?, encrypt: Boolean, input: String?, inBase64: Boolean, key: String?, iv: String?, outBase64: Boolean): String {
        return Crypto.aes(mode, encrypt, input, inBase64, key, iv, outBase64)
    }

    @Keep
    @Function
    fun rsaX(mode: String?, pub: Boolean, encrypt: Boolean, input: String?, inBase64: Boolean, key: String?, outBase64: Boolean): String {
        return Crypto.rsa(pub, encrypt, input, inBase64, key, outBase64)
    }

    @Keep
    @Function
    fun rsaEncrypt(data: String?, key: String?): String {
        return rsaEncrypt(data, key, null)
    }

    @Keep
    @Function
    fun rsaEncrypt(data: String?, key: String?, options: JSObject?): String {
        val safeData = data ?: ""
        val safeKey = key ?: ""
        var mLong = 1
        var mType = 1
        var mBlock = true
        var mConfig: String? = null
        if (options != null) {
            val op: JSONObject = JSUtils.toJsonObject(options)
            if (op.has("config")) {
                try {
                    mConfig = op.get("config") as? String
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("type")) {
                try {
                    mType = (op.get("type") as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("long")) {
                try {
                    mLong = (op.get("long") as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = op.get("block") as Boolean
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return try {
            when (mType) {
                1 -> if (mConfig != null) {
                    RSAEncrypt.encryptByPublicKey(safeData, safeKey, mConfig, mLong, mBlock) ?: ""
                } else {
                    RSAEncrypt.encryptByPublicKey(safeData, safeKey, mLong, mBlock) ?: ""
                }
                2 -> if (mConfig != null) {
                    RSAEncrypt.encryptByPrivateKey(safeData, safeKey, mConfig, mLong, mBlock) ?: ""
                } else {
                    RSAEncrypt.encryptByPrivateKey(safeData, safeKey, mLong, mBlock) ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun rsaDecrypt(encryptBase64Data: String?, key: String?): String {
        return rsaDecrypt(encryptBase64Data, key, null)
    }

    @Keep
    @Function
    fun rsaDecrypt(encryptBase64Data: String?, key: String?, options: JSObject?): String {
        val safeData = encryptBase64Data ?: ""
        val safeKey = key ?: ""
        var mLong = 1
        var mType = 1
        var mBlock = true
        var mConfig: String? = null
        if (options != null) {
            val op: JSONObject = JSUtils.toJsonObject(options)
            if (op.has("config")) {
                try {
                    mConfig = op.get("config") as? String
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("type")) {
                try {
                    mType = (op.get("type") as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("long")) {
                try {
                    mLong = (op.get("long") as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = op.get("block") as Boolean
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return try {
            when (mType) {
                1 -> if (mConfig != null) {
                    RSAEncrypt.decryptByPrivateKey(safeData, safeKey, mConfig, mLong, mBlock) ?: ""
                } else {
                    RSAEncrypt.decryptByPrivateKey(safeData, safeKey, mLong, mBlock) ?: ""
                }
                2 -> if (mConfig != null) {
                    RSAEncrypt.decryptByPublicKey(safeData, safeKey, mConfig, mLong, mBlock) ?: ""
                } else {
                    RSAEncrypt.decryptByPublicKey(safeData, safeKey, mLong, mBlock) ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun req(url: String, options: JSObject): JSObject? {
        val rt = runtime ?: return null
        return try {
            val req = Req.objectFrom(JSUtils.toJsonObject(options).toString())!!
            val res = Connect.to(url, req).execute()
            Connect.success(rt, req, res)
        } catch (e: Exception) {
            Connect.error(rt)
        }
    }

    @Keep
    @Function
    fun _http(url: String, options: JSObject): JSObject? {
        val complete = options.getJSFunction("complete")
        if (complete == null) return req(url, options)
        val req = Req.objectFrom(JSUtils.toJsonObject(options).toString())!!
        Connect.to(url, req).enqueue(getCallback(complete, req))
        return null
    }

    @Keep
    @Function
    fun setTimeout(func: JSFunction, delay: Int?) {
        func.hold()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!executor.isShutdown) executor.submit { func.call() }
            }
        }, (delay ?: 0).toLong())
    }

    private fun getCallback(complete: JSFunction, req: Req): Callback {
        return object : Callback {
            override fun onResponse(call: Call, res: Response) {
                executor.submit {
                    val rt = runtime ?: return@submit
                    complete.call(Connect.success(rt, req, res))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                executor.submit {
                    val rt = runtime ?: return@submit
                    complete.call(Connect.error(rt))
                }
            }
        }
    }

    @Keep
    @ContextSetter
    fun setJSContext(runtime: QuickJSContext) {
        this.runtime = runtime
    }
}
