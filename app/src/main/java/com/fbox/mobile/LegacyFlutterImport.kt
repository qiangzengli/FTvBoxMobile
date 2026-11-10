package com.fbox.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.orhanobut.hawk.Hawk
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.ArrayList
import java.util.HashMap

object LegacyFlutterImport {
    private const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    @JvmStatic
    fun run(context: Context) {
        val state: SharedPreferences = context.getSharedPreferences("native_migration", Context.MODE_PRIVATE)
        if (state.getBoolean("flutter_imported", false)) return
        val legacy: SharedPreferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        try {
            val selected = legacy.getString("flutter.source.current", "") ?: ""
            val selectedSubscription = if (selected.contains("::")) selected.substring(0, selected.indexOf("::")) else ""
            val subscriptions = array(legacy.getString("flutter.subscriptions", "[]") ?: "[]")
            var activeUrl = ""
            var activeRoot = ""
            val history: ArrayList<String> = Hawk.get(HawkConfig.API_HISTORY, ArrayList<String>())
            for (entry in subscriptions) {
                val sub = entry.asJsonObject
                val root = text(sub, "url")
                val line = text(sub, "selectedUrl")
                val effective = if (line.isEmpty()) root else line
                if (effective.isEmpty()) continue
                if (!history.contains(root)) history.add(root)
                if (!history.contains(effective)) history.add(effective)
                if (activeUrl.isEmpty() || selectedSubscription == text(sub, "id")) {
                    activeUrl = effective
                    activeRoot = root
                }
            }
            if (!Hawk.contains(HawkConfig.API_URL) && activeUrl.isNotEmpty()) {
                Hawk.put(HawkConfig.API_URL, activeUrl)
                Hawk.put(HawkConfig.API_HISTORY, history)
                Hawk.put(HawkConfig.HOME_API, sourceKey(selected))
                if (activeRoot != activeUrl) {
                    Hawk.put(HawkConfig.API_LINE_SOURCE, activeRoot)
                    val lines = ArrayList<String>()
                    lines.add(HistoryHelper.buildApiLine("已选线路", activeUrl))
                    Hawk.put(HawkConfig.API_LINE_LIST, lines)
                }
            }
            val media: MutableMap<String, VodInfo> = HashMap()
            for (entry in array(legacy.getString("flutter.library.media", "[]") ?: "[]")) {
                val item = entry.asJsonObject
                val key = text(item, "sourceKey")
                val id = text(item, "id")
                if (key.isEmpty() || id.startsWith("msearch:")) continue
                val vod = VodInfo()
                vod.id = if (text(item, "detailId").isEmpty()) id else text(item, "detailId")
                vod.sourceKey = sourceKey(key)
                vod.name = text(item, "title")
                vod.pic = text(item, "posterUrl")
                vod.note = text(item, "note")
                vod.type = text(item, "category")
                vod.actor = text(item, "actors")
                vod.director = text(item, "director")
                vod.des = text(item, "description")
                media["$key::$id"] = vod
            }
            val watched = stringList(legacy.getString("flutter.library.history", "") ?: "")
            for (i in watched.size - 1 downTo 0) {
                val vod = media[watched[i]]
                if (vod != null && RoomDataManger.getVodInfo(vod.sourceKey, vod.id) == null) {
                    RoomDataManger.insertVodRecord(vod.sourceKey, vod)
                }
            }
            for (id in stringList(legacy.getString("flutter.library.favorites", "") ?: "")) {
                val vod = media[id]
                if (vod != null) {
                    RoomDataManger.insertVodCollect(vod.sourceKey, vod)
                }
            }
            state.edit().putBoolean("flutter_imported", true).apply()
        } catch (error: Exception) {
            Log.e("FBoxMigration", "Flutter data import could not finish", error)
        }
    }

    @JvmStatic
    fun sourceKey(identity: String): String {
        val split = identity.indexOf("::")
        return if (split < 0) identity else identity.substring(split + 2)
    }

    private fun array(json: String): JsonArray {
        return JsonParser.parseString(json).asJsonArray
    }

    private fun text(jsonObject: JsonObject, key: String): String {
        val value: JsonElement? = jsonObject.get(key)
        return if (value == null || value.isJsonNull) "" else value.asString
    }

    @JvmStatic
    @Throws(Exception::class)
    fun stringList(stored: String): List<String> {
        var str = stored
        val values = ArrayList<String>()
        if (str.isEmpty()) return values
        if (str.startsWith("$LIST_PREFIX!")) {
            str = str.substring(LIST_PREFIX.length + 1)
        }
        if (str.startsWith("[")) {
            for (value in array(str)) {
                values.add(value.asString)
            }
        } else if (str.startsWith(LIST_PREFIX)) {
            val bytes = Base64.decode(str.substring(LIST_PREFIX.length), Base64.DEFAULT)
            ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
                val decoded = input.readObject()
                if (decoded is List<*>) {
                    for (value in decoded) {
                        if (value is String) {
                            values.add(value)
                        }
                    }
                }
            }
        }
        return values
    }
}
