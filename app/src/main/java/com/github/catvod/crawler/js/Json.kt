package com.github.catvod.crawler.js

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashMap

object Json {

    @JvmStatic
    fun valid(text: String?): Boolean {
        if (text == null) return false
        return try {
            JSONObject(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun invalid(text: String?): Boolean {
        return !valid(text)
    }

    @JvmStatic
    fun safeString(obj: JsonObject?, key: String?): String {
        return try {
            obj?.getAsJsonPrimitive(key)?.asString?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    fun safeListString(obj: JsonObject?, key: String?): List<String> {
        val result = ArrayList<String>()
        if (obj == null || key == null || !obj.has(key)) return result
        if (obj.get(key).isJsonObject) {
            result.add(safeString(obj, key))
        } else {
            for (opt in obj.getAsJsonArray(key)) result.add(opt.asString)
        }
        return result
    }

    @JvmStatic
    fun safeListElement(obj: JsonObject?, key: String?): List<JsonElement> {
        val result = ArrayList<JsonElement>()
        if (obj == null || key == null || !obj.has(key)) return result
        if (obj.get(key).isJsonObject) result.add(obj.get(key).asJsonObject)
        for (opt in obj.getAsJsonArray(key)) result.add(opt.asJsonObject)
        return result
    }

    @JvmStatic
    fun safeObject(element: JsonElement?): JsonObject {
        var el = element
        return try {
            if (el != null && el.isJsonPrimitive) el = JsonParser.parseString(el.asJsonPrimitive.asString)
            el?.asJsonObject ?: JsonObject()
        } catch (e: Exception) {
            JsonObject()
        }
    }

    @JvmStatic
    fun toMap(element: JsonElement?): Map<String, String> {
        val map = HashMap<String, String>()
        val obj = safeObject(element)
        for (key in obj.keySet()) {
            map[key] = safeString(obj, key)
        }
        return map
    }
}
