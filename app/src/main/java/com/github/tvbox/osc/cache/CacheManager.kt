package com.github.tvbox.osc.cache

import com.github.tvbox.osc.data.AppDataManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
object CacheManager {
    //反序列,把二进制数据转换成java object对象
    private fun toObject(data: ByteArray): Any? {
        var bais: ByteArrayInputStream? = null
        var ois: ObjectInputStream? = null
        return try {
            bais = ByteArrayInputStream(data)
            ois = ObjectInputStream(bais)
            ois.readObject()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                bais?.close()
                ois?.close()
            } catch (ignore: Exception) {
                ignore.printStackTrace()
            }
        }
    }

    //序列化存储数据需要转换成二进制
    private fun <T> toByteArray(body: T): ByteArray {
        var baos: ByteArrayOutputStream? = null
        var oos: ObjectOutputStream? = null
        return try {
            baos = ByteArrayOutputStream()
            oos = ObjectOutputStream(baos)
            oos.writeObject(body)
            oos.flush()
            baos.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        } finally {
            try {
                baos?.close()
                oos?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun <T> delete(key: String, body: T) {
        val cache = Cache()
        cache.key = key
        cache.data = toByteArray(body)
        AppDataManager.get().getCacheDao().delete(cache)
    }

    @JvmStatic
    fun <T> save(key: String, body: T) {
        val cache = Cache()
        cache.key = key
        cache.data = toByteArray(body)
        AppDataManager.get().getCacheDao().save(cache)
    }

    @JvmStatic
    fun getCache(key: String): Any? {
        val cache = AppDataManager.get().getCacheDao().getCache(key)
        if (cache != null && cache.data != null) {
            return toObject(cache.data!!)
        }
        return null
    }
}
