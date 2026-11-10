package com.github.tvbox.osc.util

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * <b>类名称：</b> MD5 <br/>
 * <b>类描述：</b> MD5值计算<br/>
 * <b>创建人：</b> 林肯 <br/>
 * <b>修改人：</b> 编辑人 <br/>
 * <b>修改时间：</b> 2015年08月11日 下午2:41 <br/>
 * <b>修改备注：</b> <br/>
 *
 * @version 1.0.0 <br/>
 */
object MD5 {
    private val hexDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private var sDigest: MessageDigest? = null

    init {
        try {
            sDigest = MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            Log.e("获取MD5信息摘要失败", e.message ?: "")
        }
    }

    @JvmStatic
    fun encode(res: String): String {
        val strTemp = res.toByteArray()
        return encode(strTemp) ?: ""
    }

    private fun encode(bytes: ByteArray): String? {
        return try {
            val digest = sDigest ?: MessageDigest.getInstance("MD5")
            digest.update(bytes)
            val md = digest.digest()
            val j = md.size
            val str = CharArray(j * 2)
            var k = 0
            for (byte0 in md) {
                str[k++] = hexDigits[byte0.toInt() ushr 4 and 0xf]
                str[k++] = hexDigits[byte0.toInt() and 0xf]
            }
            String(str)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getFileMd5(f: File): String {
        val sb = StringBuilder()
        try {
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(4096)
            val fis = FileInputStream(f)
            var len: Int
            while (fis.read(buffer).also { len = it } != -1) {
                md.update(buffer, 0, len)
            }
            fis.close()
            val b = md.digest()
            for (byteVal in b) {
                var d = byteVal.toInt()
                if (d < 0) {
                    d = byteVal.toInt() and 0xff
                }
                if (d < 16) sb.append("0")
                sb.append(Integer.toHexString(d))
            }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    @JvmStatic
    fun string2MD5(inStr: String?): String {
        if (sDigest == null || TextUtils.isEmpty(inStr)) {
            return ""
        }
        val charArray = inStr!!.toCharArray()
        val byteArray = ByteArray(charArray.size)
        for (i in charArray.indices) byteArray[i] = charArray[i].code.toByte()
        val md5Bytes = sDigest!!.digest(byteArray)
        val hexValue = StringBuilder()
        for (md5Byte in md5Bytes) {
            val `val` = md5Byte.toInt() and 0xff
            if (`val` < 16) hexValue.append("0")
            hexValue.append(Integer.toHexString(`val`))
        }
        return hexValue.toString()
    }

    @JvmStatic
    fun encrypt(strSource: String?): String? {
        if (sDigest == null) {
            Log.e("MD5", "MD5信息摘要初始化失败")
            return null
        } else if (TextUtils.isEmpty(strSource)) {
            Log.e("MD5", "参数strSource不能为空")
            return null
        }
        return try {
            val md5Bytes = sDigest!!.digest(strSource!!.toByteArray(charset("utf-8")))
            val encryptBytes = Base64.encode(md5Bytes, Base64.DEFAULT)
            val strEncrypt = String(encryptBytes, charset("utf-8"))
            strEncrypt.substring(0, strEncrypt.length - 1)
        } catch (e: UnsupportedEncodingException) {
            Log.e("MD5", "加密模块暂不支持此字符集合$e")
            null
        }
    }

    @JvmStatic
    fun encrypt4login(strSource: String?, appSecert: String?): String? {
        val str = encrypt(strSource) + appSecert
        return string2MD5(str)
    }
}
