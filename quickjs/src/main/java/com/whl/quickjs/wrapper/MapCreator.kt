package com.whl.quickjs.wrapper

import java.lang.reflect.Array
import java.util.HashMap

interface MapCreator {
    fun createMap(): MutableMap<Any?, Any?> = HashMap()
    fun create(): MutableMap<Any?, Any?> = createMap()
    fun toMap(): MutableMap<Any?, Any?> = createMap()

    companion object {
        @JvmStatic
        fun create(ctx: QuickJSContext, map: Map<*, *>?): JSObject = toObj(ctx, map)

        @JvmStatic
        fun createJSObject(ctx: QuickJSContext, map: Map<*, *>?): JSObject = toObj(ctx, map)

        @JvmStatic
        fun toObj(ctx: QuickJSContext, map: Map<*, *>?): JSObject {
            val obj = ctx.createNewJSObject()
            if (map.isNullOrEmpty()) return obj
            for ((key, value) in map) {
                if (key == null) continue
                ctx.setProperty(obj, key.toString(), toJSValue(ctx, value))
            }
            return obj
        }

        @JvmStatic
        fun toObject(ctx: QuickJSContext, map: Map<*, *>?): JSObject = toObj(ctx, map)

        @JvmStatic
        fun toJSValue(ctx: QuickJSContext, value: Any?): Any? {
            if (value == null) return null
            if (value is JSObject || value is JSCallFunction) return value
            if (value is Map<*, *>) return toObj(ctx, value)
            if (value is List<*>) return toArray(ctx, value)
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

        @JvmStatic
        fun toArray(ctx: QuickJSContext, list: List<*>?): JSArray {
            val array = ctx.createNewJSArray()
            if (list.isNullOrEmpty()) return array
            for (i in list.indices) {
                array.set(toJSValue(ctx, list[i]), i)
            }
            return array
        }

        @JvmStatic
        fun toArray(ctx: QuickJSContext, bytes: ByteArray?): JSArray {
            val array = ctx.createNewJSArray()
            if (bytes == null || bytes.isEmpty()) return array
            for (i in bytes.indices) {
                array.set(bytes[i].toInt(), i)
            }
            return array
        }
    }
}
