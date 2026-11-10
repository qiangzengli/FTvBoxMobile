package com.github.catvod.crawler.js.rsa

import android.util.Log
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA 非对称加密算法，加解密工具类，
 * 加密长度 不能超过 128 个字节。
 */
object RSAEncrypt {

    val TAG: String = RSAEncrypt::class.java.simpleName + " --> "

    /**
     * 标准 jdk 加密填充方式，加解密算法/工作模式/填充方式
     */
    const val ECB_PKCS1_PADDING = "RSA/ECB/PKCS1Padding"

    /**
     * RSA 加密算法
     */
    const val KEY_ALGORITHM = "RSA"

    /**
     * RSA 最大加密明文大小
     */
    private const val MAX_ENCRYPT_BLOCK = 117

    /**
     * RSA最大解密密文大小
     */
    private const val MAX_DECRYPT_BLOCK = 128

    /**
     * 随机生成 RSA 密钥对
     *
     * @param keyLength 密钥长度，范围：512～2048，一般：1024
     */
    @JvmStatic
    fun getKeyPair(keyLength: Int): KeyPair? {
        return try {
            val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
            generator.initialize(keyLength)
            generator.genKeyPair()
        } catch (e: Exception) {
            handleException(e)
            null
        }
    }

    /**
     * 获取公钥 Base64 编码
     *
     * @param publicKey 公钥
     */
    @JvmStatic
    fun getPublicKeyBase64(publicKey: PublicKey): String {
        return DataUtils.base64Encode(publicKey.encoded)
    }

    /**
     * 获取私钥 Base64 编码
     *
     * @param privateKey 公钥
     */
    @JvmStatic
    fun getPrivateKeyBase64(privateKey: PrivateKey): String {
        return DataUtils.base64Encode(privateKey.encoded)
    }

    /**
     * 获取 PublicKey 对象
     *
     * @param pubKey 公钥，X509 格式de
     */
    @JvmStatic
    fun getPublicKey(pubKey: String): PublicKey? {
        try {
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(DataUtils.base64Decode(pubKey)))
        } catch (unused: NoSuchAlgorithmException) {
            handleException(Exception("无此算法"))
        } catch (unused2: InvalidKeySpecException) {
            handleException(Exception("公钥非法"))
        } catch (unused3: NullPointerException) {
            handleException(Exception("公钥数据为空"))
        }
        return null
    }

    /**
     * 获取 PrivateKey 对象
     *
     * @param prvKey 私钥，PKCS8 格式
     */
    @JvmStatic
    fun getPrivateKey(prvKey: String): PrivateKey? {
        try {
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(DataUtils.base64Decode(prvKey)))
        } catch (unused: NoSuchAlgorithmException) {
            handleException(Exception("无此算法"))
        } catch (unused2: InvalidKeySpecException) {
            handleException(Exception("私钥非法"))
        } catch (unused3: NullPointerException) {
            handleException(Exception("私钥数据为空"))
        }
        return null
    }

    // --------------------- 1 公钥加密，私钥解密 ---------------------

    @JvmStatic
    fun encryptByPublicKey(data: String, pubKey: String, mlong: Int, block: Boolean): String? {
        return encryptByPublicKey(data, pubKey, ECB_PKCS1_PADDING, mlong, block)
    }

    @JvmStatic
    fun encryptByPublicKey(data: String, pubKey: String, config: String, mlong: Int, block: Boolean): String? {
        try {
            val bytes = data.toByteArray(charset("UTF-8"))
            val cipher = Cipher.getInstance(config)
            val rSAPublicKey = getPublicKey(pubKey) as? RSAPublicKey ?: return null
            cipher.init(Cipher.ENCRYPT_MODE, rSAPublicKey)
            if (mlong == 1) {
                return DataUtils.base64Encode(cipher.doFinal(bytes))
            }
            var bitLength = MAX_ENCRYPT_BLOCK
            if (block) {
                bitLength = rSAPublicKey.modulus.bitLength() / 8 - 11
            }
            val inputLen = bytes.size
            val out = ByteArrayOutputStream()
            var offSet = 0
            var i = 0
            var cache: ByteArray
            while (inputLen - offSet > 0) {
                cache = if (inputLen - offSet > bitLength) {
                    cipher.doFinal(bytes, offSet, bitLength)
                } else {
                    cipher.doFinal(bytes, offSet, inputLen - offSet)
                }
                out.write(cache, 0, cache.size)
                i++
                offSet = i * bitLength
            }
            val encryptedData = out.toByteArray()
            out.close()
            return DataUtils.base64Encode(encryptedData)
        } catch (e: Exception) {
            handleException(e)
        }
        return null
    }

    @JvmStatic
    fun decryptByPrivateKey(encryptBase64Data: String, prvKey: String, mlong: Int, block: Boolean): String? {
        return decryptByPrivateKey(encryptBase64Data, prvKey, ECB_PKCS1_PADDING, mlong, block)
    }

    @JvmStatic
    fun decryptByPrivateKey(encryptBase64Data: String, prvKey: String, config: String, mlong: Int, block: Boolean): String? {
        try {
            val encryptedData = DataUtils.base64Decode(encryptBase64Data)
            val cipher = Cipher.getInstance(config)
            val rSAPrivateKey = getPrivateKey(prvKey) as? RSAPrivateKey ?: return null
            cipher.init(Cipher.DECRYPT_MODE, rSAPrivateKey)
            if (mlong == 1) {
                return String(cipher.doFinal(encryptedData))
            }
            var bitLength = MAX_DECRYPT_BLOCK
            if (block) {
                bitLength = rSAPrivateKey.modulus.bitLength() / 8
            }
            val inputLen = encryptedData.size
            val out = ByteArrayOutputStream()
            var offSet = 0
            var i = 0
            var cache: ByteArray
            while (inputLen - offSet > 0) {
                cache = if (inputLen - offSet > bitLength) {
                    cipher.doFinal(encryptedData, offSet, bitLength)
                } else {
                    cipher.doFinal(encryptedData, offSet, inputLen - offSet)
                }
                out.write(cache, 0, cache.size)
                i++
                offSet = i * bitLength
            }
            out.close()
            return out.toString("UTF-8")
        } catch (e: Exception) {
            handleException(e)
        }
        return null
    }

    // --------------------- 2 私钥加密，公钥解密 ---------------------

    @JvmStatic
    fun encryptByPrivateKey(data: String, prvKey: String, mlong: Int, block: Boolean): String? {
        return encryptByPrivateKey(data, prvKey, ECB_PKCS1_PADDING, mlong, block)
    }

    @JvmStatic
    fun encryptByPrivateKey(data: String, prvKey: String, config: String, mlong: Int, block: Boolean): String? {
        try {
            val bytes = data.toByteArray(charset("UTF-8"))
            val cipher = Cipher.getInstance(config)
            val rSAPrivateKey = getPrivateKey(prvKey) as? RSAPrivateKey ?: return null
            cipher.init(Cipher.ENCRYPT_MODE, rSAPrivateKey)
            if (mlong == 1) {
                return DataUtils.base64Encode(cipher.doFinal(bytes))
            }
            var bitLength = MAX_ENCRYPT_BLOCK
            if (block) {
                bitLength = rSAPrivateKey.modulus.bitLength() / 8 - 11
            }
            val inputLen = bytes.size
            val out = ByteArrayOutputStream()
            var offSet = 0
            var i = 0
            var cache: ByteArray
            while (inputLen - offSet > 0) {
                cache = if (inputLen - offSet > bitLength) {
                    cipher.doFinal(bytes, offSet, bitLength)
                } else {
                    cipher.doFinal(bytes, offSet, inputLen - offSet)
                }
                out.write(cache, 0, cache.size)
                i++
                offSet = i * bitLength
            }
            val encryptedData = out.toByteArray()
            out.close()
            return DataUtils.base64Encode(encryptedData)
        } catch (e: Exception) {
            handleException(e)
        }
        return null
    }

    @JvmStatic
    fun decryptByPublicKey(encryptBase64Data: String, pubKey: String, mlong: Int, block: Boolean): String? {
        return decryptByPublicKey(encryptBase64Data, pubKey, ECB_PKCS1_PADDING, mlong, block)
    }

    @JvmStatic
    fun decryptByPublicKey(encryptBase64Data: String, pubKey: String, config: String, mlong: Int, block: Boolean): String? {
        try {
            val encryptedData = DataUtils.base64Decode(encryptBase64Data)
            val cipher = Cipher.getInstance(config)
            val rSAPublicKey = getPublicKey(pubKey) as? RSAPublicKey ?: return null
            cipher.init(Cipher.DECRYPT_MODE, rSAPublicKey)
            if (mlong == 1) {
                return String(cipher.doFinal(encryptedData))
            }
            var bitLength = MAX_DECRYPT_BLOCK
            if (block) {
                bitLength = rSAPublicKey.modulus.bitLength() / 8
            }
            val inputLen = encryptedData.size
            val out = ByteArrayOutputStream()
            var offSet = 0
            var i = 0
            var cache: ByteArray
            while (inputLen - offSet > 0) {
                cache = if (inputLen - offSet > bitLength) {
                    cipher.doFinal(encryptedData, offSet, bitLength)
                } else {
                    cipher.doFinal(encryptedData, offSet, inputLen - offSet)
                }
                out.write(cache, 0, cache.size)
                i++
                offSet = i * bitLength
            }
            out.close()
            return out.toString("UTF-8")
        } catch (e: Exception) {
            handleException(e)
        }
        return null
    }

    private fun handleException(e: Exception) {
        e.printStackTrace()
        Log.e(TAG, TAG + e)
    }
}
