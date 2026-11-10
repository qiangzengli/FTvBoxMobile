package com.whl.quickjs.wrapper

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array

open class JSUtils<T> {
    companion object {
        @JvmStatic
        fun isEmpty(obj: Any?): Boolean {
            return when (obj) {
                null -> true
                is CharSequence -> obj.isEmpty()
                is Collection<*> -> obj.isEmpty()
                is Map<*, *> -> obj.isEmpty()
                else -> if (obj.javaClass.isArray) Array.getLength(obj) == 0 else false
            }
        }

        @JvmStatic
        fun isNotEmpty(str: CharSequence?): Boolean = !isEmpty(str)

        @JvmStatic
        fun isNotEmpty(obj: Any?): Boolean = !isEmpty(obj)

        @JvmStatic
        fun toJsonObject(`object`: JSObject?): JSONObject {
            if (`object` == null) return JSONObject()
            return try {
                JSONObject(`object`.stringify())
            } catch (e: JSONException) {
                JSONObject()
            }
        }

        @JvmStatic
        fun toJsonArray(array: JSArray?): JSONArray {
            if (array == null) return JSONArray()
            return try {
                JSONArray(array.stringify())
            } catch (e: JSONException) {
                JSONArray()
            }
        }
    }

    open fun toArray(ctx: QuickJSContext, items: List<T>?): JSArray {
        val array = ctx.createNewJSArray()
        if (items.isNullOrEmpty()) return array
        for (i in items.indices) {
            array.set(toJSValue(ctx, items[i]), i)
        }
        return array
    }

    open fun toArray(ctx: QuickJSContext, bytes: ByteArray?): JSArray {
        val array = ctx.createNewJSArray()
        if (bytes == null || bytes.isEmpty()) return array
        for (i in bytes.indices) {
            array.set(bytes[i].toInt(), i)
        }
        return array
    }

    open fun toArray(ctx: QuickJSContext, arrays: kotlin.Array<T>?): JSArray {
        val array = ctx.createNewJSArray()
        if (arrays == null || arrays.isEmpty()) return array
        for (i in arrays.indices) {
            array.set(toJSValue(ctx, arrays[i]), i)
        }
        return array
    }

    open fun toObj(ctx: QuickJSContext, map: Map<*, *>?): JSObject {
        val obj = ctx.createNewJSObject()
        if (map.isNullOrEmpty()) return obj
        for ((key, value) in map) {
            if (key == null) continue
            ctx.setProperty(obj, key.toString(), toJSValue(ctx, value))
        }
        return obj
    }

    open fun toJSValue(ctx: QuickJSContext, value: Any?): Any? {
        if (value == null) return null
        if (value is JSObject || value is JSCallFunction) return value
        if (value is Map<*, *>) return toObj(ctx, value)
        @Suppress("UNCHECKED_CAST")
        if (value is List<*>) return toArray(ctx, value as List<T>)
        if (value is ByteArray) return toArray(ctx, value)
        val valueClass = value.javaClass
        if (valueClass.isArray) {
            val array = ctx.createNewJSArray()
            val length = Array.getLength(value)
            for (i in 0 until length) {
                array.set(toJSValue(ctx, Array.get(value, i)), i)
            }
            return array
        }
        return value
    }
}
