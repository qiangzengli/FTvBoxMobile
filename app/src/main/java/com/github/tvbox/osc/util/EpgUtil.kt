package com.github.tvbox.osc.util

import android.content.res.AssetManager
import com.github.tvbox.osc.base.App
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.HashMap

object EpgUtil {
    private var epgDoc: JsonObject? = null
    private val epgHashMap = HashMap<String, JsonObject>()

    @JvmStatic
    fun init() {
        if (epgDoc != null) return
        try {
            val assetManager: AssetManager = App.getInstance().assets
            val inputStreamReader = InputStreamReader(assetManager.open("epg_data.json"), "UTF-8")
            val br = BufferedReader(inputStreamReader)
            var line: String?
            val builder = StringBuilder()
            while (br.readLine().also { line = it } != null) {
                builder.append(line)
            }
            br.close()
            inputStreamReader.close()
            if (builder.toString().isNotEmpty()) {
                val doc: JsonObject = Gson().fromJson(builder.toString(), JsonObject::class.java as Type)
                epgDoc = doc
                val epgsArray = doc.get("epgs")?.asJsonArray
                if (epgsArray != null) {
                    for (opt in epgsArray) {
                        val obj = opt as JsonObject
                        val name = obj.get("name").asString.trim()
                        val names = name.split(",")
                        for (string in names) {
                            epgHashMap[string] = obj
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun getEpgInfo(channelName: String?): Array<String>? {
        if (channelName == null) return null
        try {
            if (epgHashMap.containsKey(channelName)) {
                val obj = epgHashMap[channelName] ?: return null
                return arrayOf(
                    obj.get("logo")?.asString ?: "",
                    obj.get("epgid")?.asString ?: ""
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }
}
