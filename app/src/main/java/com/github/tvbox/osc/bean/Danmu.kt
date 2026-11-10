package com.github.tvbox.osc.bean

import android.text.TextUtils
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.ArrayList
import java.util.Collections
import java.util.regex.Pattern

class Danmu {
    fun interface CancelChecker {
        fun isCancelled(): Boolean
    }

    private val data = ArrayList<Data>()

    fun getData(): List<Data> {
        return if (data.isEmpty()) Collections.emptyList() else data
    }

    class Data(
        private val param: String?,
        private val text: String?
    ) {
        fun getParam(): String {
            return if (TextUtils.isEmpty(param)) "" else param!!
        }

        fun getText(): String {
            return if (TextUtils.isEmpty(text)) "" else text!!
        }
    }

    companion object {
        private val D_TAG_PATTERN = Pattern.compile(
            "<d\\s+[^>]*\\bp\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</d>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        @JvmStatic
        fun fromXml(xml: String?): Danmu {
            return fromXml(xml, 0)
        }

        @JvmStatic
        fun fromXml(xml: String?, startPosition: Long): Danmu {
            return fromXml(xml, startPosition, null)
        }

        @JvmStatic
        fun fromXml(xml: String?, startPosition: Long, cancelChecker: CancelChecker?): Danmu {
            val danmu = Danmu()
            if (TextUtils.isEmpty(xml)) return danmu
            val position = Math.max(0, startPosition)
            if (position > 0) {
                parseByTag(danmu, xml!!, position, cancelChecker)
                if (isCancelled(cancelChecker)) return danmu
                if (danmu.data.isNotEmpty()) return danmu
            }
            val fixedXml = escapeIllegalEntities(xml!!)
            if (parseByXmlPull(danmu, fixedXml, position, cancelChecker)) return danmu
            if (isCancelled(cancelChecker)) return danmu
            danmu.data.clear()
            parseByTag(danmu, xml, position, cancelChecker)
            return danmu
        }

        private fun parseByXmlPull(
            danmu: Danmu, xml: String, startPosition: Long,
            cancelChecker: CancelChecker?
        ): Boolean {
            return try {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (isCancelled(cancelChecker)) return false
                    if (eventType == XmlPullParser.START_TAG && "d" == parser.name) {
                        val param = parser.getAttributeValue(null, "p")
                        val text = parser.nextText()
                        if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)
                            && (startPosition <= 0 || getTime(param) >= startPosition)
                        ) {
                            danmu.data.add(Data(param, text))
                        }
                    }
                    eventType = parser.next()
                }
                danmu.data.isNotEmpty()
            } catch (th: Throwable) {
                false
            }
        }

        private fun parseByTag(
            danmu: Danmu, xml: String, startPosition: Long,
            cancelChecker: CancelChecker?
        ) {
            val matcher = D_TAG_PATTERN.matcher(xml)
            while (matcher.find()) {
                if (isCancelled(cancelChecker)) return
                val param = decodeXmlString(matcher.group(2))
                if (TextUtils.isEmpty(param) || (startPosition > 0 && getTime(param) < startPosition)) continue
                val text = matcher.group(3)
                if (!TextUtils.isEmpty(text)) danmu.data.add(Data(param, text))
            }
        }

        private fun isCancelled(cancelChecker: CancelChecker?): Boolean {
            return cancelChecker != null && cancelChecker.isCancelled()
        }

        private fun getTime(param: String?): Long {
            val end = param?.indexOf(',') ?: -1
            if (end <= 0) return Long.MIN_VALUE
            return try {
                (param!!.substring(0, end).toFloat() * 1000).toLong()
            } catch (ignored: Throwable) {
                Long.MIN_VALUE
            }
        }

        private fun escapeIllegalEntities(xml: String): String {
            val length = xml.length
            var firstIllegalEntity = -1
            for (i in 0 until length) {
                val ch = xml[i]
                if (ch == '&' && !isLegalEntity(xml, i + 1)) {
                    firstIllegalEntity = i
                    break
                }
            }
            if (firstIllegalEntity < 0) return xml
            val builder = StringBuilder(length + 16)
            builder.append(xml, 0, firstIllegalEntity)
            for (i in firstIllegalEntity until length) {
                val ch = xml[i]
                if (ch == '&' && !isLegalEntity(xml, i + 1)) {
                    builder.append("&amp;")
                } else {
                    builder.append(ch)
                }
            }
            return builder.toString()
        }

        private fun isLegalEntity(text: String, start: Int): Boolean {
            val end = text.indexOf(';', start)
            if (end < 0 || end - start > 10) return false
            val entity = text.substring(start, end)
            if ("amp" == entity || "lt" == entity || "gt" == entity
                || "quot" == entity || "apos" == entity
            ) {
                return true
            }
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                return isHexNumber(entity, 2)
            }
            return entity.startsWith("#") && isDecimalNumber(entity, 1)
        }

        private fun isDecimalNumber(text: String, start: Int): Boolean {
            if (text.length <= start) return false
            for (i in start until text.length) {
                if (!Character.isDigit(text[i])) return false
            }
            return true
        }

        private fun isHexNumber(text: String, start: Int): Boolean {
            if (text.length <= start) return false
            for (i in start until text.length) {
                val ch = text[i]
                if (!Character.isDigit(ch)
                    && (ch < 'a' || ch > 'f')
                    && (ch < 'A' || ch > 'F')
                ) {
                    return false
                }
            }
            return true
        }

        private fun decodeXmlString(text: String?): String {
            if (TextUtils.isEmpty(text)) return ""
            return text!!.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
        }
    }
}
