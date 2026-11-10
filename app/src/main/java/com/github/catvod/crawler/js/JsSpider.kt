package com.github.catvod.crawler.js

import android.content.Context
import android.text.TextUtils
import android.util.Base64
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.whl.quickjs.wrapper.ContextSetter
import com.whl.quickjs.wrapper.Function
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSMethod
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.JSUtils
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.UriUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.lang.reflect.Method
import java.util.Arrays
import java.util.HashMap
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class JsSpider(key: String, api: String, cls: Class<*>?) : Spider() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val dex: Class<*>? = cls
    private var ctx: QuickJSContext? = null
    private var jsObject: JSObject? = null
    private val key: String = "J" + MD5.encode(key)
    private val api: String = api
    private var cat: Boolean = false
    private var emptyModuleBytecode: ByteArray? = null
    private val destroyed = AtomicBoolean(false)

    init {
        initializeJS()
    }

    override fun cancelByTag() {
        Connect.cancelByTag("js_okhttp_tag")
    }

    private fun createObject(): JSObject {
        return ctx!!.createNewJSObject()
    }

    private fun createArray(): JSArray {
        return ctx!!.createNewJSArray()
    }

    private fun set(jsObj: JSObject, name: String, value: Any?) {
        ctx!!.setProperty(jsObj, name, value)
    }

    private fun get(jsObj: JSObject, name: String): Any? {
        return ctx!!.getProperty(jsObj, name)
    }

    private fun toJsonArray(array: JSArray): JSONArray {
        return JSUtils.toJsonArray(array)
    }

    private fun bind(target: JSObject, receiver: Any) {
        for (method in receiver.javaClass.methods) {
            if (method.isAnnotationPresent(ContextSetter::class.java)) {
                try {
                    method.invoke(receiver, ctx)
                } catch (ignored: Throwable) {
                }
            }
        }
        for (method in receiver.javaClass.methods) {
            if (!isQuickJsMethod(method)) continue
            val name = methodName(method)
            set(target, name, JSCallFunction { args ->
                try {
                    if (args != null) method.invoke(receiver, *args) else method.invoke(receiver)
                } catch (ignored: Throwable) {
                    null
                }
            })
        }
    }

    private fun isQuickJsMethod(method: Method): Boolean {
        return method.isAnnotationPresent(Function::class.java) || method.isAnnotationPresent(JSMethod::class.java)
    }

    private fun methodName(method: Method): String {
        val function = method.getAnnotation(Function::class.java)
        if (function != null && !TextUtils.isEmpty(function.name)) return function.name
        return method.name
    }

    private fun submit(runnable: Runnable) {
        if (!destroyed.get()) executor.submit(runnable)
    }

    private fun <T> submit(callable: Callable<T>): Future<T> {
        return executor.submit(callable)
    }

    private fun call(func: String, vararg args: Any?): Any? {
        if (destroyed.get() || jsObject == null) return null
        return try {
            submit(Callable { Async.run(jsObject!!, func, args).get() }).get()
        } catch (e: InterruptedException) {
            LOG.i("Executor 提交或等待失败$e")
            null
        } catch (e: ExecutionException) {
            LOG.i("Executor 提交或等待失败$e")
            null
        }
    }

    private fun cfg(ext: String): JSObject {
        val cfg = createObject()
        set(cfg, "stype", 3)
        set(cfg, "skey", if (TextUtils.isEmpty(siteKey)) key else siteKey)
        if (Json.invalid(ext)) set(cfg, "ext", ext)
        else set(cfg, "ext", ctx!!.parse(ext) as JSObject)
        return cfg
    }

    override fun init(context: Context?, extend: String?) {
        try {
            val safeExt = extend ?: ""
            if (cat) call("init", submit(Callable { cfg(safeExt) }).get())
            else call("init", if (Json.valid(safeExt)) ctx!!.parse(safeExt) else safeExt)
        } catch (ignored: Exception) {
        }
    }

    override fun homeContent(filter: Boolean): String {
        return try {
            call("home", filter) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun homeVideoContent(): String {
        return try {
            call("homeVod") as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun categoryContent(tid: String?, pg: String?, filter: Boolean, extend: HashMap<String, String>?): String {
        return try {
            val obj = submit(Callable { JSUtils<String>().toObj(ctx!!, extend) }).get()
            call("category", tid, pg, filter, obj) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun detailContent(ids: List<String?>?): String {
        return try {
            call("detail", ids?.get(0)) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun searchContent(key: String?, quick: Boolean): String {
        return try {
            call("search", key, quick) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun playerContent(flag: String?, id: String?, vipFlags: List<String?>?): String {
        return try {
            val safeFlags = vipFlags?.filterNotNull() ?: emptyList()
            val array = submit(Callable { JSUtils<String>().toArray(ctx!!, safeFlags) }).get()
            call("play", flag, id, array) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun liveContent(url: String?): String {
        return try {
            call("live", url) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun manualVideoCheck(): Boolean {
        return try {
            call("sniffer") as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun isVideoFormat(url: String?): Boolean {
        return try {
            call("isVideo", url) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun proxyLocal(params: Map<String, String>?): Array<Any?>? {
        if (params == null) return arrayOfNulls(0)
        return try {
            if ("catvod" == params["from"]) proxy2(params)
            else submit(Callable { proxy1(params) }).get()
        } catch (e: Exception) {
            arrayOfNulls(0)
        }
    }

    override fun action(action: String?): String? {
        return try {
            call("action", action) as? String
        } catch (e: Exception) {
            null
        }
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        try {
            executor.submit {
                try {
                    jsObject = null
                    ctx?.destroy()
                } catch (th: Throwable) {
                    LOG.i("echo-js-destroy-error " + th.message)
                } finally {
                    executor.shutdown()
                }
            }
        } catch (th: Throwable) {
            executor.shutdownNow()
        }
    }

    private fun initializeJS() {
        submit(Callable {
            if (ctx == null) createCtx()
            if (dex != null) createDex()

            var content = FileUtils.loadModule(api)
            if (isInvalidModuleContent(content)) {
                return@Callable null
            }

            if (content.startsWith("//bb")) {
                cat = true
                val b = Base64.decode(content.replace("//bb", ""), 0)
                try {
                    ctx!!.execute(byteFF(b))
                    ctx!!.evaluateModule(String.format(SPIDER_STRING_CODE, "$key.js") + "globalThis." + key + " = globalThis.__JS_SPIDER__;", "tv_box_root.js")
                } catch (th: Throwable) {
                    LOG.i("echo-bytecode-execute-error " + api + ", msg=" + th.message)
                    return@Callable null
                }
            } else {
                if (content.contains("__JS_SPIDER__")) {
                    content = content.replace("__JS_SPIDER__\\s*=".toRegex(), "export default ")
                }
                if (content.contains("__jsEvalReturn") && !content.contains("export default")) {
                    cat = true
                }
                try {
                    ctx!!.evaluateModule(content, api)
                    ctx!!.evaluateModule(String.format(SPIDER_STRING_CODE, api) + "globalThis." + key + " = globalThis.__JS_SPIDER__;", "tv_box_root.js")
                } catch (th: Throwable) {
                    LOG.i("echo-evaluateModule-error " + api + ", msg=" + th.message)
                    return@Callable null
                }
            }
            jsObject = get(ctx!!.globalObject, key) as? JSObject
            jsObject?.hold()
            null
        }).get()
    }

    private fun createCtx() {
        val qCtx = QuickJSContext.create()
        ctx = qCtx
        emptyModuleBytecode = qCtx.compileModule(EMPTY_MODULE_CODE, "empty.js")
        qCtx.setModuleLoader(object : QuickJSContext.BytecodeModuleLoader() {
            override fun getModuleBytecode(moduleName: String): ByteArray {
                val ss = FileUtils.loadModule(moduleName)
                if (isInvalidModuleContent(ss)) {
                    return compileEmptyModule(moduleName)
                }
                if (ss.startsWith("//DRPY")) {
                    return try {
                        val bytes = bytecode(Base64.decode(ss.replace("//DRPY", ""), Base64.URL_SAFE))
                        bytes ?: compileEmptyModule(moduleName)
                    } catch (th: Throwable) {
                        LOG.i("echo-bytecode-module-error $moduleName, msg=${th.message}")
                        compileEmptyModule(moduleName)
                    }
                } else if (ss.startsWith("//bb")) {
                    return try {
                        val b = Base64.decode(ss.replace("//bb", ""), 0)
                        byteFF(b)
                    } catch (th: Throwable) {
                        LOG.i("echo-bytecode-module-error $moduleName, msg=${th.message}")
                        compileEmptyModule(moduleName)
                    }
                } else {
                    return compileModule(moduleName, ss)
                }
            }

            override fun moduleNormalizeName(moduleBaseName: String, moduleName: String): String {
                return UriUtil.resolve(moduleBaseName, moduleName)
            }
        })
        qCtx.setConsole(object : QuickJSContext.Console {
            override fun log(s: String) {
                LOG.i("echo-QuJs $s")
            }

            override fun info(s: String) {
                LOG.i("echo-QuJs $s")
            }

            override fun warn(s: String) {
                LOG.i("echo-QuJs $s")
            }

            override fun error(s: String) {
                LOG.i("echo-QuJs $s")
            }
        })

        bind(qCtx.globalObject, Global(executor))

        val localObj = createObject()
        set(qCtx.globalObject, "local", localObj)
        bind(localObj, local())

        val net = FileUtils.loadModule("net.js")
        if (!isInvalidModuleContent(net)) qCtx.globalObject.context.evaluate(net)
        preloadTemplate()
    }

    private fun compileEmptyModule(moduleName: String): ByteArray {
        LOG.i("echo-getModuleBytecode empty :$moduleName")
        return emptyModuleBytecode!!
    }

    private fun compileModule(moduleName: String, content: String): ByteArray {
        return try {
            if (moduleName.contains("cheerio.min.js")) {
                val bytecode = ctx!!.compileModule(content, "cheerio.min.js")
                FileUtils.setCacheByte("cheerio.min", bytecode)
                bytecode
            } else if (moduleName.contains("crypto-js.js")) {
                val bytecode = ctx!!.compileModule(content, "crypto-js.js")
                FileUtils.setCacheByte("crypto-js", bytecode)
                bytecode
            } else {
                ctx!!.compileModule(content, moduleName)
            }
        } catch (th: Throwable) {
            LOG.i("echo-compileModule-error $moduleName, msg=${th.message}")
            compileEmptyModule(moduleName)
        }
    }

    private fun isInvalidModuleContent(content: String?): Boolean {
        if (TextUtils.isEmpty(content)) return true
        var trim = content!!.trim()
        if (trim.startsWith("\uFEFF")) trim = trim.substring(1).trim()
        val lower = trim.lowercase()
        return lower.startsWith("<")
                || lower.startsWith("{\"code\":404")
                || lower.startsWith("404")
                || lower.startsWith("not found")
    }

    private fun preloadTemplate() {
        try {
            val template = "import tpl from '模板.js';\n" +
                    "globalThis.muban = tpl.muban;\n" +
                    "globalThis.getMubans = tpl.getMubans;"
            ctx!!.evaluateModule(template, "tv_box_template.js")
        } catch (th: Throwable) {
            LOG.i("echo-preloadTemplate-error " + th.message)
        }
    }

    private fun createDex() {
        try {
            val obj = createObject()
            val clz = dex!!
            val classes = clz.declaredClasses
            set(ctx!!.globalObject, "jsapi", obj)
            if (classes.isEmpty()) invokeSingle(clz, obj)
            if (classes.isNotEmpty()) invokeMultiple(clz, obj)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun invokeSingle(clz: Class<*>, jsObj: JSObject) {
        val constructor = clz.getDeclaredConstructor(QuickJSContext::class.java)
        invoke(clz, jsObj, constructor.newInstance(ctx))
    }

    private fun invokeMultiple(clz: Class<*>, jsObj: JSObject) {
        for (subClz in clz.declaredClasses) {
            val parentInstance = clz.getDeclaredConstructor(QuickJSContext::class.java).newInstance(ctx)
            val subConstructor = subClz.getDeclaredConstructor(clz)
            val javaObj = subConstructor.newInstance(parentInstance)
            val subObj = createObject()
            invoke(subClz, subObj, javaObj)
            set(jsObj, subClz.simpleName, subObj)
        }
    }

    private fun invoke(clz: Class<*>, jsObj: JSObject, javaObj: Any) {
        for (method in clz.methods) {
            if (!isQuickJsMethod(method)) continue
            invoke(jsObj, method, javaObj)
        }
    }

    private fun invoke(jsObj: JSObject, method: Method, javaObj: Any) {
        set(jsObj, methodName(method), JSCallFunction { objects ->
            try {
                if (objects != null) method.invoke(javaObj, *objects) else method.invoke(javaObj)
            } catch (e: Throwable) {
                null
            }
        })
    }

    private fun proxy1(params: Map<String, String>): Array<Any?> {
        val objectParam = JSUtils<String>().toObj(ctx!!, params)
        val array = toJsonArray(jsObject!!.getJSFunction("proxy").call(objectParam) as JSArray)
        val headerAvailable = array.length() > 3 && array.opt(3) != null
        val result = arrayOfNulls<Any>(4)
        result[0] = array.opt(0)
        result[1] = array.opt(1)
        result[2] = getStream(array.opt(2))
        result[3] = if (headerAvailable) getHeader(array.opt(3)) else null
        if (array.length() > 4) {
            try {
                if (array.optInt(4) == 1) {
                    var content = array.optString(2)
                    if (content.contains("base64,")) content = content.substring(content.indexOf("base64,") + 7)
                    result[2] = ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    private fun getHeader(headerRaw: Any?): Map<String, String> {
        val headers = HashMap<String, String>()
        if (headerRaw is JSONObject) {
            val json: JSONObject = headerRaw
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headers[key] = json.optString(key)
            }
        } else if (headerRaw is String) {
            try {
                val json = JSONObject(headerRaw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    headers[key] = json.optString(key)
                }
            } catch (e: JSONException) {
                LOG.i("getHeader: 无法解析 String 为 JSON$e")
            }
        } else if (headerRaw is Map<*, *>) {
            for ((key, value) in headerRaw) {
                headers[key.toString()] = value.toString()
            }
        }
        return headers
    }

    private fun proxy2(params: Map<String, String>): Array<Any?> {
        val url = params["url"] ?: ""
        val header = params["header"] ?: ""
        val parts = Arrays.asList(*url.split("/".toRegex()).toTypedArray())
        val array = submit(Callable { JSUtils<String>().toArray(ctx!!, parts) }).get()
        val objectParsed = submit(Callable { ctx!!.parse(header) }).get()
        val json = call("proxy", array, objectParsed) as? String
        val res = Res.objectFrom(json) ?: return arrayOfNulls(0)
        var contentType = res.getContentType()
        if (TextUtils.isEmpty(contentType)) contentType = "application/octet-stream"
        val result = arrayOfNulls<Any>(3)
        result[0] = 200
        result[1] = contentType
        if (res.getBuffer() == 2) {
            result[2] = ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT))
        } else {
            result[2] = ByteArrayInputStream(res.getContent().toByteArray())
        }
        return result
    }

    private fun getStream(o: Any?): ByteArrayInputStream {
        return if (o is JSONArray) {
            val bytes = ByteArray(o.length())
            for (i in 0 until o.length()) bytes[i] = o.optInt(i).toByte()
            ByteArrayInputStream(bytes)
        } else {
            ByteArrayInputStream(o.toString().toByteArray())
        }
    }

    companion object {
        private const val BYTECODE_VERSION: Byte = 67
        private const val EMPTY_MODULE_CODE =
            "const empty = null;\n" +
                    "export default empty;\n" +
                    "export const JSEncrypt = empty;\n" +
                    "export const NodeRSA = empty;\n" +
                    "export const pako = empty;\n" +
                    "export const JSON5 = empty;\n" +
                    "export const mb = empty;\n" +
                    "export const parse = empty;\n" +
                    "export const stringify = empty;\n" +
                    "export const inflate = empty;\n" +
                    "export const deflate = empty;\n" +
                    "export const gzip = empty;\n" +
                    "export const ungzip = empty;\n" +
                    "export const encrypt = empty;\n" +
                    "export const decrypt = empty;"

        private const val SPIDER_STRING_CODE = "import * as spider from '%s'\n\n" +
                "if (!globalThis.__JS_SPIDER__) {\n" +
                "    if (spider.__jsEvalReturn) {\n" +
                "        globalThis.req = http\n" +
                "        globalThis.__JS_SPIDER__ = spider.__jsEvalReturn()\n" +
                "        globalThis.__JS_SPIDER__.is_cat = true\n" +
                "    } else if (spider.default) {\n" +
                "        globalThis.__JS_SPIDER__ = typeof spider.default === 'function' ? spider.default() : spider.default\n" +
                "    }\n" +
                "}\n"

        @JvmStatic
        fun byteFF(bytes: ByteArray): ByteArray {
            val newBt = ByteArray(bytes.size - 4)
            newBt[0] = BYTECODE_VERSION
            System.arraycopy(bytes, 5, newBt, 1, bytes.size - 5)
            return newBt
        }

        private fun bytecode(bytes: ByteArray?): ByteArray? {
            if (bytes == null || bytes.isEmpty()) return null
            if (bytes[0] == BYTECODE_VERSION) return bytes
            LOG.i("echo-bytecode-version-mismatch actual=" + bytes[0] + ", expected=" + BYTECODE_VERSION)
            return null
        }
    }
}
