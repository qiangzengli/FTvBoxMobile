package com.github.catvod.crawler.js

import android.util.Base64
import com.github.tvbox.osc.util.OkGoHelper
import com.google.common.net.HttpHeaders
import com.lzy.okgo.OkGo
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.JSUtils
import com.whl.quickjs.wrapper.QuickJSContext
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.nio.charset.Charset
import java.util.Random

object Connect {
    @JvmField
    var client: OkHttpClient? = null

    @JvmStatic
    fun to(url: String, req: Req): Call {
        val c = OkGoHelper.getDefaultClient()
        client = c
        return c.newCall(getRequest(url, req, Headers.of(req.getHeader())))
    }

    @JvmStatic
    fun success(ctx: QuickJSContext, req: Req, res: Response): JSObject {
        return try {
            val jsObject = ctx.createNewJSObject()
            val jsHeader = ctx.createNewJSObject()
            setHeader(ctx, res, jsHeader)
            ctx.setProperty(jsObject, "headers", jsHeader)
            val body = res.body()
            val bytes = body?.bytes() ?: ByteArray(0)
            if (req.getBuffer() == 0) ctx.setProperty(jsObject, "content", String(bytes, Charset.forName(req.getCharset())))
            if (req.getBuffer() == 1) {
                val array = ctx.createNewJSArray()
                for (i in bytes.indices) array.set(bytes[i].toInt(), i)
                ctx.setProperty(jsObject, "content", array)
            }
            if (req.getBuffer() == 2) ctx.setProperty(jsObject, "content", Base64.encodeToString(bytes, Base64.DEFAULT or Base64.NO_WRAP))
            jsObject
        } catch (e: Exception) {
            error(ctx)
        }
    }

    @JvmStatic
    fun error(ctx: QuickJSContext): JSObject {
        val jsObject = ctx.createNewJSObject()
        val jsHeader = ctx.createNewJSObject()
        ctx.setProperty(jsObject, "headers", jsHeader)
        ctx.setProperty(jsObject, "content", "")
        return jsObject
    }

    private fun getRequest(url: String, req: Req, headers: Headers): Request {
        return if (req.getMethod().equals("post", ignoreCase = true)) {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).post(getPostBody(req, headers.get(HttpHeaders.CONTENT_TYPE))).build()
        } else if (req.getMethod().equals("header", ignoreCase = true)) {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).head().build()
        } else {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).get().build()
        }
    }

    private fun getPostBody(req: Req, contentType: String?): RequestBody {
        if (req.getData() != null && req.getPostType() == "json") return getJsonBody(req)
        if (req.getData() != null && req.getPostType() == "form") return getFormBody(req)
        if (req.getData() != null && req.getPostType() == "form-data") return getFormDataBody(req)
        if (req.getBody() != null && contentType != null) return RequestBody.create(MediaType.get(contentType), req.getBody()!!)
        return RequestBody.create(null, "")
    }

    private fun getJsonBody(req: Req): RequestBody {
        return RequestBody.create(MediaType.get("application/json"), req.getData().toString())
    }

    private fun getFormBody(req: Req): RequestBody {
        val formBody = FormBody.Builder()
        val params = Json.toMap(req.getData())
        for ((key, value) in params) formBody.add(key, value)
        return formBody.build()
    }

    private fun getFormDataBody(req: Req): RequestBody {
        val boundary = "--dio-boundary-" + Random().nextInt(42949) + "" + Random().nextInt(67296)
        val builder = MultipartBody.Builder(boundary).setType(MultipartBody.FORM)
        val params = Json.toMap(req.getData())
        for ((key, value) in params) builder.addFormDataPart(key, value)
        return builder.build()
    }

    private fun setHeader(ctx: QuickJSContext, res: Response, jsObject: JSObject) {
        for ((key, value) in res.headers().toMultimap()) {
            if (value.size == 1) ctx.setProperty(jsObject, key, value[0])
            if (value.size >= 2) ctx.setProperty(jsObject, key, JSUtils<String>().toArray(ctx, value))
        }
    }

    @JvmStatic
    fun cancelByTag(tag: Any?) {
        try {
            val c = client
            if (c != null && tag != null) {
                for (call in c.dispatcher().queuedCalls()) {
                    if (tag == call.request().tag()) {
                        call.cancel()
                    }
                }
                for (call in c.dispatcher().runningCalls()) {
                    if (tag == call.request().tag()) {
                        call.cancel()
                    }
                }
            }
            if (tag != null) {
                OkGo.getInstance().cancelTag(tag)
                cancelDefaultClient(tag)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun cancelDefaultClient(tag: Any?) {
        val defaultClient = OkGoHelper.getDefaultClient()
        if (defaultClient == null || tag == null) return
        for (call in defaultClient.dispatcher().queuedCalls()) {
            if (tag == call.request().tag()) {
                call.cancel()
            }
        }
        for (call in defaultClient.dispatcher().runningCalls()) {
            if (tag == call.request().tag()) {
                call.cancel()
            }
        }
    }
}
