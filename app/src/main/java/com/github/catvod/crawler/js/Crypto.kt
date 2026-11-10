package com.github.catvod.crawler.js

import android.util.Base64
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    @JvmStatic
    fun aes(mode: String?, encrypt: Boolean, input: String?, inBase64: Boolean, key: String?, iv: String?, outBase64: Boolean): String {
        return try {
            var keyBuf = (key ?: "").toByteArray()
            if (keyBuf.size < 16) keyBuf = Arrays.copyOf(keyBuf, 16)
            var ivBuf = iv?.toByteArray() ?: ByteArray(0)
            if (ivBuf.size < 16) ivBuf = Arrays.copyOf(ivBuf, 16)
            val cipher = Cipher.getInstance(mode + "Padding")
            val keySpec = SecretKeySpec(keyBuf, "AES")
            if (iv == null) cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec)
            else cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBuf))
            val safeInput = input ?: ""
            val inBuf = if (inBase64) Base64.decode(safeInput.replace("_", "/").replace("-", "+"), Base64.DEFAULT) else safeInput.toByteArray(charset("UTF-8"))
            if (outBase64) Base64.encodeToString(cipher.doFinal(inBuf), Base64.NO_WRAP) else String(cipher.doFinal(inBuf), charset("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun rsa(pub: Boolean, encrypt: Boolean, input: String?, inBase64: Boolean, key: String?, outBase64: Boolean): String {
        return try {
            val safeKey = key ?: ""
            val safeInput = input ?: ""
            val rsaKey = generateKey(pub, safeKey)
            val len = getModulusLength(rsaKey)
            var outBytes = ByteArray(0)
            val inBytes = if (inBase64) Base64.decode(safeInput.replace("_", "/").replace("-", "+"), Base64.DEFAULT) else safeInput.toByteArray(charset("UTF-8"))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, rsaKey)
            val blockLen = if (encrypt) len / 8 - 11 else len / 8
            var bufIdx = 0
            while (bufIdx < inBytes.size) {
                val bufEndIdx = Math.min(bufIdx + blockLen, inBytes.size)
                val tmpInBytes = ByteArray(bufEndIdx - bufIdx)
                System.arraycopy(inBytes, bufIdx, tmpInBytes, 0, tmpInBytes.size)
                val tmpBytes = cipher.doFinal(tmpInBytes)
                bufIdx = bufEndIdx
                outBytes = concatArrays(outBytes, tmpBytes)
            }
            if (outBase64) Base64.encodeToString(outBytes, Base64.NO_WRAP) else String(outBytes, charset("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun generateKey(pub: Boolean, key: String): Key {
        var cleanKey = key
        if (pub) {
            cleanKey = cleanKey.replace("\r\n", "").replace("\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(cleanKey, Base64.DEFAULT)))
        } else {
            cleanKey = cleanKey.replace("\r\n", "").replace("\n", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(cleanKey, Base64.DEFAULT)))
        }
    }

    private fun getModulusLength(key: Key): Int {
        return if (key is PublicKey) (key as RSAPublicKey).modulus.bitLength()
        else (key as RSAPrivateKey).modulus.bitLength()
    }

    private fun concatArrays(a: ByteArray, b: ByteArray): ByteArray {
        val aLen = a.size
        val bLen = b.size
        val result = ByteArray(aLen + bLen)
        System.arraycopy(a, 0, result, 0, aLen)
        System.arraycopy(b, 0, result, aLen, bLen)
        return result
    }
}
