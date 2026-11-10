package com.github.tvbox.osc.util

object StringUtils {

    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean {
        return str == null || str.isEmpty()
    }

    @JvmStatic
    fun isNotEmpty(str: CharSequence?): Boolean {
        return !isEmpty(str)
    }

    @JvmStatic
    fun isNull(obj: Any?): Boolean {
        return obj == null
    }

    @JvmStatic
    fun isNotNull(obj: Any?): Boolean {
        return !isNull(obj)
    }

    @JvmStatic
    fun isEmpty(obj: Any?): Boolean {
        if (obj == null) return true
        if (obj is CharSequence) return obj.isEmpty()
        if (obj is Collection<*>) return obj.isEmpty()
        if (obj is Map<*, *>) return obj.isEmpty()
        if (obj.javaClass.isArray) return java.lang.reflect.Array.getLength(obj) == 0
        return false
    }

    @JvmStatic
    fun isNotEmpty(obj: Any?): Boolean {
        return !isEmpty(obj)
    }

    private val U2028 = String(byteArrayOf(0xE2.toByte(), 0x80.toByte(), 0xA8.toByte()))
    private val U2029 = String(byteArrayOf(0xE2.toByte(), 0x80.toByte(), 0xA9.toByte()))

    /**
     * Escape JavaScript string
     *
     * @param line unescaped string
     * @return escaped string
     */
    @JvmStatic
    fun escapeJavaScriptString(line: String): String {
        val sb = StringBuilder()
        for (i in line.indices) {
            val c = line[i]
            when (c) {
                '"', '\'', '\\' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
            .replace(U2028, "\u2028")
            .replace(U2029, "\u2029")
    }

    @JvmStatic
    fun getBaseUrl(url: String?): String? {
        if (isEmpty(url)) {
            return url
        }
        val baseUrls = url!!.replace("http://", "").replace("https://", "")
        val baseUrl2 = baseUrls.split("/".toRegex()).toTypedArray()[0]
        return if (url.startsWith("https")) {
            "https://$baseUrl2"
        } else {
            "http://$baseUrl2"
        }
    }

    @JvmStatic
    fun arrayToString(list: Array<String>?, fromIndex: Int, cha: String): String {
        return arrayToString(list, fromIndex, list?.size ?: 0, cha)
    }

    @JvmStatic
    fun arrayToString(list: Array<String>?, fromIndex: Int, endIndex: Int, cha: String): String {
        val builder = StringBuilder()
        if (list == null || list.size <= fromIndex) {
            return ""
        } else if (list.size <= 1) {
            return list[0]
        } else {
            builder.append(list[fromIndex])
        }
        var i = 1 + fromIndex
        while (i < list.size && i < endIndex) {
            builder.append(cha).append(list[i])
            i++
        }
        return builder.toString()
    }

    @JvmStatic
    fun listToString(list: List<String>?, cha: String): String {
        val builder = StringBuilder()
        if (list.isNullOrEmpty()) {
            return ""
        } else if (list.size <= 1) {
            return list[0]
        } else {
            builder.append(list[0])
        }
        for (i in 1 until list.size) {
            builder.append(cha).append(list[i])
        }
        return builder.toString()
    }

    @JvmStatic
    fun listToString(list: List<String>?, fromIndex: Int, cha: String): String {
        val builder = StringBuilder()
        if (list == null || list.size <= fromIndex) {
            return ""
        } else if (list.size <= 1) {
            return list[0]
        } else {
            builder.append(list[fromIndex])
        }
        for (i in fromIndex + 1 until list.size) {
            builder.append(cha).append(list[i])
        }
        return builder.toString()
    }

    @JvmStatic
    fun listToString(list: List<String>?): String {
        return listToString(list, "&&")
    }

    @JvmStatic
    fun isBlank(text: String?): Boolean {
        return trim(text).isEmpty()
    }

    @JvmStatic
    fun trimBlanks(str: String?): String? {
        if (str.isNullOrEmpty()) {
            return str
        }
        val len = str.length
        var st = 0

        while (st < len && (str[st] == '\n' || str[st] == '\r' || str[st] == '\u000c' || str[st] == '\t')) {
            st++
        }
        var end = len
        while (st < end && (str[end - 1] == '\n' || str[end - 1] == '\r' || str[end - 1] == '\u000c' || str[end - 1] == '\t')) {
            end--
        }
        return if (st > 0 || end < str.length) str.substring(st, end) else str
    }

    @JvmStatic
    fun trim(string: String?): String {
        if (string.isNullOrEmpty() || " " == string) return ""
        var start = 0
        val len = string.length
        var end = len - 1
        while (start < end && (string[start] <= ' ' || string[start] == '　')) {
            ++start
        }
        while (start < end && (string[end] <= ' ' || string[end] == '　')) {
            --end
        }
        if (end < len) ++end
        return if (start > 0 || end < len) string.substring(start, end) else string
    }

    @JvmStatic
    fun isJsonType(text: String?): Boolean {
        var result = false
        if (isNotEmpty(text)) {
            val t = trim(text)
            if (t.startsWith("{") && t.endsWith("}")) {
                result = true
            } else if (t.startsWith("[") && t.endsWith("]")) {
                result = true
            }
        }
        return result
    }

    @JvmStatic
    fun isJsonObject(text: String?): Boolean {
        var result = false
        if (isNotEmpty(text)) {
            val t = trim(text)
            if (t.startsWith("{") && t.endsWith("}")) {
                result = true
            }
        }
        return result
    }

    @JvmStatic
    fun isJsonArray(text: String?): Boolean {
        var result = false
        if (isNotEmpty(text)) {
            val t = trim(text)
            if (t.startsWith("[") && t.endsWith("]")) {
                result = true
            }
        }
        return result
    }
}
