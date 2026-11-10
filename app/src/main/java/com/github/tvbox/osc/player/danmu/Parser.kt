package com.github.tvbox.osc.player.danmu

import android.graphics.Color
import android.text.TextUtils
import com.github.tvbox.osc.bean.Danmu
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat
import master.flame.danmaku.danmaku.model.AlphaValue
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.Duration
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.SpecialDanmaku
import master.flame.danmaku.danmaku.model.android.DanmakuFactory
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.util.DanmakuUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class Parser @JvmOverloads constructor(
    input: String,
    private val cancelChecker: CancelChecker? = null,
    initialPosition: Long = 0
) : BaseDanmakuParser() {

    fun interface CancelChecker {
        fun isCancelled(): Boolean
    }

    private val initialPosition: Long = Math.max(0, initialPosition)
    private val danmu: Danmu = Danmu.fromXml(
        resolveContent(input),
        this.initialPosition,
        if (cancelChecker == null) null else Danmu.CancelChecker { cancelChecker.isCancelled() }
    )

    private var scaleX: Float = 0f
    private var scaleY: Float = 0f
    private var index: Int = 0
    private var beforeCursor: Int = 0
    private var afterCursor: Int = 0
    private var initialComplete: Boolean = false

    fun getDanmuCount(): Int {
        return danmu.data.size
    }

    private fun resolveContent(input: String?): String {
        if (isCancelled()) return ""
        if (TextUtils.isEmpty(input)) return ""
        val source = input!!.trim()
        if (source.startsWith("file")) return FileUtils.read(source)
        if (source.startsWith("http")) {
            try {
                if (isCancelled()) return ""
                val response = executeHttp(source)
                if (isCancelled()) {
                    response.close()
                    return ""
                }
                val content = readBody(response)
                LOG.i(
                    "echo-danmu http code: " + response.code() +
                            ", encoding: " + response.header("Content-Encoding", "") +
                            ", length: " + content.length
                )
                return content
            } catch (e: SocketTimeoutException) {
                if (isLocalProxy(source)) {
                    try {
                        LOG.e("echo-danmu load timeout, retry local proxy")
                        if (isCancelled()) return ""
                        val response = executeHttp(source)
                        if (isCancelled()) {
                            response.close()
                            return ""
                        }
                        val content = readBody(response)
                        LOG.i(
                            "echo-danmu retry http code: " + response.code() +
                                    ", encoding: " + response.header("Content-Encoding", "") +
                                    ", length: " + content.length
                        )
                        return content
                    } catch (retryError: Throwable) {
                        LOG.e("echo-danmu retry error: " + retryError.message)
                    }
                }
                LOG.e("echo-danmu load error: timeout")
                return ""
            } catch (th: Throwable) {
                if (source.startsWith("https") && isSslError(th)) {
                    try {
                        LOG.i("echo-danmu ssl verify failed, retry unsafe ssl")
                        if (isCancelled()) return ""
                        val response = executeUnsafeHttp(source)
                        if (isCancelled()) {
                            response.close()
                            return ""
                        }
                        val content = readBody(response)
                        LOG.i(
                            "echo-danmu unsafe ssl http code: " + response.code() +
                                    ", encoding: " + response.header("Content-Encoding", "") +
                                    ", length: " + content.length
                        )
                        return content
                    } catch (retryError: Throwable) {
                        LOG.e("echo-danmu unsafe ssl retry error: " + retryError.message)
                    }
                }
                LOG.e("echo-danmu load error: " + th.message)
                return ""
            }
        }
        return source
    }

    private fun isCancelled(): Boolean {
        return cancelChecker != null && cancelChecker.isCancelled()
    }

    @Throws(IOException::class)
    private fun executeHttp(source: String): okhttp3.Response {
        val request = Request.Builder().url(source).get().build()
        return HTTP_CLIENT.newCall(request).execute()
    }

    @Throws(IOException::class)
    private fun executeUnsafeHttp(source: String): okhttp3.Response {
        val request = Request.Builder().url(source).get().build()
        return UNSAFE_HTTP_CLIENT.newCall(request).execute()
    }

    private fun isSslError(th: Throwable?): Boolean {
        var current = th
        while (current != null) {
            if (current is SSLException) return true
            current = current.cause
        }
        return false
    }

    private fun isLocalProxy(source: String): Boolean {
        return source.startsWith("http://127.0.0.1:") || source.startsWith("http://localhost:")
    }

    @Throws(IOException::class)
    private fun readBody(response: okhttp3.Response): String {
        val body = response.body() ?: return ""
        var bytes = body.bytes()
        if (looksXml(bytes)) return String(bytes, charset("UTF-8"))
        val encoding = response.header("Content-Encoding", "")
        if ("gzip".equals(encoding, ignoreCase = true)) {
            bytes = readAll(GZIPInputStream(ByteArrayInputStream(bytes)))
        } else if ("deflate".equals(encoding, ignoreCase = true)) {
            bytes = inflate(bytes)
        }
        return String(bytes, charset("UTF-8"))
    }

    private fun looksXml(bytes: ByteArray?): Boolean {
        if (bytes == null) return false
        for (value in bytes) {
            val ch = (value.toInt() and 0xff).toChar()
            if (Character.isWhitespace(ch)) continue
            return ch == '<'
        }
        return false
    }

    @Throws(IOException::class)
    private fun inflate(bytes: ByteArray): ByteArray {
        return try {
            readAll(InflaterInputStream(ByteArrayInputStream(bytes)))
        } catch (e: IOException) {
            val inflater = Inflater(true)
            try {
                readAll(InflaterInputStream(ByteArrayInputStream(bytes), inflater))
            } finally {
                inflater.end()
            }
        }
    }

    @Throws(IOException::class)
    private fun readAll(input: InputStream): ByteArray {
        try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        } finally {
            input.close()
        }
    }

    override fun parse(): Danmakus {
        val data = danmu.data
        val start = 0
        val end = Math.min(data.size, start + INITIAL_BATCH_SIZE)
        beforeCursor = start
        afterCursor = end
        val items = createDanmakus(data, start, end)
        initialComplete = !isCancelled()
        LOG.i("echo-danmu rendered initial count: " + items.size + ", pending: " + (data.size - (end - start)))
        val result = Danmakus(IDanmakus.ST_BY_TIME)
        for (item in items) {
            result.addItem(item)
        }
        return result
    }

    @Synchronized
    fun hasMoreDanmaku(): Boolean {
        return !initialComplete || beforeCursor > 0 || afterCursor < danmu.data.size
    }

    @Synchronized
    fun parseNextBatch(): List<BaseDanmaku> {
        val data = danmu.data
        val from: Int
        val to: Int
        if (afterCursor < data.size) {
            from = afterCursor
            to = Math.min(data.size, from + APPEND_BATCH_SIZE)
            afterCursor = to
        } else if (beforeCursor > 0) {
            to = beforeCursor
            from = Math.max(0, to - APPEND_BATCH_SIZE)
            beforeCursor = from
        } else {
            return Collections.emptyList()
        }
        return createDanmakus(data, from, to)
    }

    private fun createDanmakus(data: List<Danmu.Data>, from: Int, to: Int): List<BaseDanmaku> {
        val items = ArrayList<BaseDanmaku>(to - from)
        for (i in from until to) {
            if (isCancelled()) break
            val danmaku = createDanmaku(data[i])
            if (danmaku != null) items.add(danmaku)
        }
        return items
    }

    override fun setDisplayer(display: IDisplayer): BaseDanmakuParser {
        super.setDisplayer(display)
        scaleX = mDispWidth / DanmakuFactory.BILI_PLAYER_WIDTH
        scaleY = mDispHeight / DanmakuFactory.BILI_PLAYER_HEIGHT
        return this
    }

    private fun createDanmaku(data: Danmu.Data): BaseDanmaku? {
        return try {
            val param = data.param ?: return null
            val values = param.split(",".toRegex(), limit = 5).toTypedArray()
            if (values.size < 4) return null
            val type = values[1].toInt()
            val item = mContext.mDanmakuFactory.createDanmaku(type, mContext) ?: return null
            val time = (values[0].toFloat() * 1000).toLong()
            val size = values[2].toFloat() * Math.max(1.0f, mDispDensity - 0.6f)
            val color = if (DanmuHelper.useRandomColor()) {
                DanmuHelper.randomColor()
            } else {
                ((0x00000000ff000000L or values[3].toLong()) and 0x00000000ffffffffL).toInt()
            }
            item.time = time
            item.setTimer(mTimer)
            item.textSize = size
            item.textColor = color
            item.textShadowColor = if (color <= Color.BLACK) Color.WHITE else Color.BLACK
            item.flags = mContext.mGlobalFlagValues
            item.index = index++
            val text = data.text ?: ""
            DanmakuUtils.fillText(item, decodeXmlString(text))
            if (item.type == BaseDanmaku.TYPE_SPECIAL &&
                text.startsWith("[") &&
                text.endsWith("]") &&
                !setSpecial(item)
            ) {
                return null
            }
            item
        } catch (ignored: Throwable) {
            null
        }
    }

    private fun setSpecial(item: BaseDanmaku): Boolean {
        val textArr: Array<String>
        try {
            val jsonArray = JSONArray(item.text.toString())
            textArr = Array(jsonArray.length()) { i -> jsonArray.getString(i) }
        } catch (e: JSONException) {
            return false
        }
        if (textArr.size < 5 || TextUtils.isEmpty(textArr[4])) return false
        return try {
            DanmakuUtils.fillText(item, textArr[4])
            var beginX = textArr[0].toFloat()
            var beginY = textArr[1].toFloat()
            var endX = beginX
            var endY = beginY
            val alphaArr = textArr[2].split("-".toRegex()).toTypedArray()
            val beginAlpha = (AlphaValue.MAX * alphaArr[0].toFloat()).toInt()
            val endAlpha = if (alphaArr.size > 1) {
                (AlphaValue.MAX * alphaArr[1].toFloat()).toInt()
            } else {
                beginAlpha
            }
            val alphaDuration = (textArr[3].toFloat() * 1000).toLong()
            var translationDuration = alphaDuration
            var translationStartDelay: Long = 0
            var rotateY = 0f
            var rotateZ = 0f
            if (textArr.size >= 7) {
                rotateZ = textArr[5].toFloat()
                rotateY = textArr[6].toFloat()
            }
            if (textArr.size >= 11) {
                endX = textArr[7].toFloat()
                endY = textArr[8].toFloat()
                if (!TextUtils.isEmpty(textArr[9])) translationDuration = textArr[9].toLong()
                if (!TextUtils.isEmpty(textArr[10])) translationStartDelay = textArr[10].toFloat().toLong()
            }
            if (isPercentageNumber(textArr[0])) beginX *= DanmakuFactory.BILI_PLAYER_WIDTH
            if (isPercentageNumber(textArr[1])) beginY *= DanmakuFactory.BILI_PLAYER_HEIGHT
            if (textArr.size >= 8 && isPercentageNumber(textArr[7])) endX *= DanmakuFactory.BILI_PLAYER_WIDTH
            if (textArr.size >= 9 && isPercentageNumber(textArr[8])) endY *= DanmakuFactory.BILI_PLAYER_HEIGHT
            item.duration = Duration(alphaDuration)
            item.rotationZ = rotateZ
            item.rotationY = rotateY
            mContext.mDanmakuFactory.fillTranslationData(
                item, beginX, beginY, endX, endY,
                translationDuration, translationStartDelay, scaleX, scaleY
            )
            mContext.mDanmakuFactory.fillAlphaData(item, beginAlpha, endAlpha, alphaDuration)
            if (textArr.size >= 12 && "true".equals(textArr[11], ignoreCase = true)) {
                item.textShadowColor = Color.TRANSPARENT
            }
            if (textArr.size >= 14 && item is SpecialDanmaku) {
                item.isQuadraticEaseOut = "0" == textArr[13]
            }
            if (textArr.size >= 15 && !TextUtils.isEmpty(textArr[14])) {
                fillLinePath(item, textArr[14])
            }
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun fillLinePath(item: BaseDanmaku, motionPathString: String) {
        val motionPath = motionPathString.substring(1)
        if (TextUtils.isEmpty(motionPath)) return
        val pointStrArray = motionPath.split("L".toRegex()).toTypedArray()
        val points = Array(pointStrArray.size) { FloatArray(2) }
        for (i in pointStrArray.indices) {
            val pointArray = pointStrArray[i].split(",".toRegex()).toTypedArray()
            if (pointArray.size < 2) return
            points[i][0] = pointArray[0].toFloat()
            points[i][1] = pointArray[1].toFloat()
        }
        DanmakuFactory.fillLinePathData(item, points, scaleX, scaleY)
    }

    private fun isPercentageNumber(number: String?): Boolean {
        return number != null && number.contains(".")
    }

    private fun decodeXmlString(text: String?): String {
        if (TextUtils.isEmpty(text)) return ""
        val s = text!!
        if (s.indexOf('&') < 0) return s
        return s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
    }

    companion object {
        private const val HTTP_TIMEOUT_MS = 20 * 1000L
        private const val INITIAL_BATCH_SIZE = 500
        private const val APPEND_BATCH_SIZE = 1000

        private val TRUST_ALL_CERT = object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }

        private val TRUST_ALL_HOSTNAME = HostnameVerifier { _, _ -> true }

        @Volatile
        private var HTTP_CLIENT = buildHttpClient(false)

        @Volatile
        private var UNSAFE_HTTP_CLIENT = buildHttpClient(true)

        private fun buildHttpClient(unsafeSsl: Boolean): OkHttpClient {
            val base = OkGoHelper.getDefaultClient()
            val builder = if (base != null) {
                base.newBuilder()
            } else {
                OkHttpClient.Builder().proxySelector(OkGoHelper.proxySelector()).proxyAuthenticator(OkGoHelper.proxyAuthenticator())
            }
            builder.readTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            builder.writeTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            builder.connectTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            builder.retryOnConnectionFailure(true)
            if (unsafeSsl) {
                val sslSocketFactory: SSLSocketFactory = SSLSocketFactoryCompat(TRUST_ALL_CERT)
                builder.sslSocketFactory(sslSocketFactory, TRUST_ALL_CERT)
                builder.hostnameVerifier(TRUST_ALL_HOSTNAME)
            }
            return builder.build()
        }

        @JvmStatic
        @Synchronized
        fun resetHttpClient() {
            HTTP_CLIENT = buildHttpClient(false)
            UNSAFE_HTTP_CLIENT = buildHttpClient(true)
        }
    }
}
