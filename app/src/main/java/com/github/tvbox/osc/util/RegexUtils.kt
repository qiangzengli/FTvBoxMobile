package com.github.tvbox.osc.util

import java.util.HashMap
import java.util.regex.Pattern

object RegexUtils {
    private val patternCache: MutableMap<String, Pattern> = HashMap()

    @JvmStatic
    fun getPattern(regex: String): Pattern {
        var pattern = patternCache[regex]
        if (pattern == null) {
            pattern = Pattern.compile(regex)
            patternCache[regex] = pattern
        }
        return pattern!!
    }

    @JvmStatic
    fun getPattern(regex: String, flag: Int): Pattern {
        val key = "$regex@$flag"
        var pattern = patternCache[key]
        if (pattern == null) {
            pattern = Pattern.compile(regex, flag)
            patternCache[key] = pattern
        }
        return pattern!!
    }
}
