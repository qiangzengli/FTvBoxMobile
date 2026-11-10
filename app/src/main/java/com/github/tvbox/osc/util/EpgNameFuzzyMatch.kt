package com.github.tvbox.osc.util

import android.content.res.AssetManager
import com.github.tvbox.osc.base.App
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.lzy.okgo.request.GetRequest
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.Hashtable

object EpgNameFuzzyMatch {
    private var epgNameDoc: JsonObject? = null
    private val hsEpgName = Hashtable<String, JsonObject>()

    @JvmStatic
    fun init() {
        if (epgNameDoc != null) return
        val gson = Gson()
        try {
            val assetManager: AssetManager = App.getInstance().assets
            val inputStreamReader = InputStreamReader(assetManager.open("Roinlong_Epg.json"), "UTF-8")
            val br = BufferedReader(inputStreamReader)
            var line: String?
            val builder = StringBuilder()
            while (br.readLine().also { line = it } != null) {
                builder.append(line)
            }
            br.close()
            inputStreamReader.close()
            if (builder.toString().isNotEmpty()) {
                val jsonObj: JsonObject = gson.fromJson(builder.toString(), JsonObject::class.java as Type)
                epgNameDoc = jsonObj
                hasAddData(jsonObj)
                return
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 上述两种途径都失败后,读取网络自定义文件中的内容
        val request: GetRequest<String> = OkGo.get<String>("http://www.baidu.com/maotv/epg.json")
        request.headers("User-Agent", UA.random())
        request.execute(object : AbsCallback<String>() {
            override fun onSuccess(response: Response<String>) {
                try {
                    val pageStr = response.body()
                    val infoJson: JsonObject = gson.fromJson(pageStr, JsonObject::class.java as Type)
                    epgNameDoc = infoJson
                    hasAddData(infoJson)
                } catch (ex: Exception) {
                }
            }

            override fun onError(response: Response<String>) {
                super.onError(response)
            }

            override fun onFinish() {
                super.onFinish()
            }

            override fun convertResponse(response: okhttp3.Response): String {
                return response.body()?.string() ?: ""
            }
        })
    }

    @JvmStatic
    fun hasAddData(epgNameDoc: JsonObject?) {
        val epgsArray = epgNameDoc?.get("epgs")?.asJsonArray ?: return
        for (opt in epgsArray) {
            val obj = opt.asJsonObject
            val name = obj.get("name")?.asString?.trim() ?: continue
            val names = name.split(",")
            for (string in names) {
                hsEpgName[string] = obj
            }
        }
    }

    @JvmStatic
    fun getEpgNameInfo(channelName: String?): JsonObject? {
        if (channelName != null && hsEpgName.containsKey(channelName)) {
            return hsEpgName[channelName]
        }
        return null
    }
}
