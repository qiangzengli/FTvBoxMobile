package com.github.tvbox.osc.util.parser

import android.util.Base64
import com.github.catvod.crawler.SpiderDebug
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 并发解析，直到获得第一个结果
 */
object JsonParallel {
    private var client: OkHttpClient? = null
    private var executorService: ExecutorService? = null
    private val futures = ArrayList<Future<JSONObject?>>()

    @JvmStatic
    fun parse(jx: LinkedHashMap<String, String>?, url: String?): JSONObject {
        try {
            if (jx != null && jx.isNotEmpty()) {
                val okHttpClient = OkHttpClient()
                client = okHttpClient
                val executor = Executors.newFixedThreadPool(5)
                executorService = executor
                val completionService: CompletionService<JSONObject?> = ExecutorCompletionService(executor)
                futures.clear()

                for (jxName in jx.keys) {
                    val parseUrl = jx[jxName] ?: continue
                    futures.add(
                        completionService.submit(
                            Callable {
                                try {
                                    val reqHeaders = getReqHeader(parseUrl)
                                    val realUrl = reqHeaders["url"] ?: ""
                                    reqHeaders.remove("url")
                                    val headers = Headers.of(reqHeaders)
                                    val request = Request.Builder()
                                        .url(realUrl + url)
                                        .headers(headers)
                                        .tag("ParseTag")
                                        .build()

                                    val call: Call = okHttpClient.newCall(request)
                                    val response: Response = call.execute()
                                    val json = response.body()?.string() ?: ""

                                    val taskResult = Utils.jsonParse(url ?: "", json)
                                    taskResult?.put("jxFrom", jxName)
                                    taskResult
                                } catch (th: Throwable) {
                                    null
                                }
                            }
                        )
                    )
                }

                var pTaskResult: JSONObject? = null
                for (i in futures.indices) {
                    val completed = completionService.take()
                    try {
                        pTaskResult = completed.get()
                        if (pTaskResult != null) {
                            okHttpClient.dispatcher().cancelAll()
                            for (future in futures) {
                                try {
                                    future.cancel(true)
                                } catch (t: Throwable) {
                                    SpiderDebug.log(t)
                                }
                            }
                            futures.clear()
                            break
                        }
                    } catch (th: Throwable) {
                        SpiderDebug.log(th)
                    }
                }
                executor.shutdownNow()
                if (pTaskResult != null) return pTaskResult
            }
        } catch (th: Throwable) {
            SpiderDebug.log(th)
        }
        return JSONObject()
    }

    @JvmStatic
    fun cancelTasks() {
        if (client != null) {
            client?.dispatcher()?.cancelAll()
        }
        for (future in futures) {
            try {
                future.cancel(true)
            } catch (ignored: Throwable) {
            }
        }
        futures.clear()
        if (executorService != null) {
            executorService?.shutdownNow()
        }
    }

    @JvmStatic
    fun getReqHeader(url: String): HashMap<String, String> {
        val reqHeaders = HashMap<String, String>()
        reqHeaders["url"] = url
        if (url.contains("cat_ext")) {
            try {
                val start = url.indexOf("cat_ext=")
                val end = url.indexOf("&", start)
                val ext = if (end != -1) url.substring(start + 8, end) else url.substring(start + 8)
                val decodedExt = String(Base64.decode(ext, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
                val newUrl = if (end != -1) url.substring(0, start) + url.substring(end + 1) else url.substring(0, start)
                val jsonObject = JSONObject(decodedExt)
                if (jsonObject.has("header")) {
                    val headerJson = jsonObject.optJSONObject("header")
                    if (headerJson != null) {
                        val keys = headerJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            reqHeaders[key] = headerJson.optString(key, "")
                        }
                    }
                }
                reqHeaders["url"] = newUrl
            } catch (ignored: Throwable) {
            }
        }
        return reqHeaders
    }
}
