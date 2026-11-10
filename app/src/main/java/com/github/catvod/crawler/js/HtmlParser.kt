package com.github.catvod.crawler.js

import android.text.TextUtils
import com.github.tvbox.osc.util.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList
import java.util.Arrays
import java.util.regex.Pattern

object HtmlParser {
    private var pdfh_html: String = ""
    private var pdfa_html: String = ""
    private val p = Pattern.compile("url\\((.*?)\\)", Pattern.MULTILINE or Pattern.DOTALL)
    private val NOADD_INDEX = Pattern.compile(":eq|:lt|:gt|:first|:last|^body$|^#") // 不自动加eq下标索引
    private val URLJOIN_ATTR = Pattern.compile("(url|src|href|-original|-src|-play|-url|style)$", Pattern.MULTILINE or Pattern.CASE_INSENSITIVE) // 需要自动urljoin的属性
    private val SPECIAL_URL = Pattern.compile("^(ftp|magnet|thunder|ws):", Pattern.MULTILINE or Pattern.CASE_INSENSITIVE) // 过滤特殊链接,不走urlJoin
    private var pdfh_doc: Document? = null
    private var pdfa_doc: Document? = null

    @JvmStatic
    fun joinUrl(parent: String?, child: String): String {
        if (StringUtils.isEmpty(parent)) {
            return child
        }
        var q = parent!!
        try {
            val url = URL(URL(parent), child)
            q = url.toExternalForm()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }
        return q
    }

    class Painfo {
        @JvmField
        var nparse_rule: String? = null
        @JvmField
        var nparse_index: Int = 0
        @JvmField
        var excludes: MutableList<String>? = null
    }

    private fun getParseInfo(nparse: String): Painfo {
        val painfo = Painfo()
        painfo.nparse_rule = nparse
        if (nparse.contains(":eq")) {
            val parts = nparse.split(":")
            painfo.nparse_rule = parts[0]
            var nparse_pos = parts[1]

            if (painfo.nparse_rule!!.contains("--")) {
                val rules = painfo.nparse_rule!!.split("--".toRegex()).toTypedArray()
                painfo.excludes = ArrayList(Arrays.asList(*rules))
                painfo.excludes!!.removeAt(0)
                painfo.nparse_rule = rules[0]
            } else if (nparse_pos.contains("--")) {
                val rules = nparse_pos.split("--".toRegex()).toTypedArray()
                painfo.excludes = ArrayList(Arrays.asList(*rules))
                painfo.excludes!!.removeAt(0)
                nparse_pos = rules[0]
            }

            try {
                painfo.nparse_index = nparse_pos.replace("eq(", "").replace(")", "").toInt()
            } catch (e1: Exception) {
                painfo.nparse_index = 0
            }
        } else {
            if (nparse.contains("--")) {
                val rules = painfo.nparse_rule!!.split("--".toRegex()).toTypedArray()
                painfo.excludes = ArrayList(Arrays.asList(*rules))
                painfo.excludes!!.removeAt(0)
                painfo.nparse_rule = rules[0]
            }
        }
        return painfo
    }

    @JvmStatic
    fun isIndex(str: String?): Boolean {
        if (StringUtils.isEmpty(str)) {
            return false
        }
        for (str2 in arrayOf(":eq", ":lt", ":gt", ":first", ":last", "body", "#")) {
            if (str!!.contains(str2)) {
                if (str2 == "body" || str2 == "#") {
                    return str.startsWith(str2)
                }
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isUrl(str: String?): Boolean {
        if (StringUtils.isEmpty(str)) {
            return false
        }
        for (str2 in arrayOf("url", "src", "href", "-original", "-play")) {
            if (str!!.contains(str2)) {
                return true
            }
        }
        return false
    }

    private fun parseHikerToJq(parse: String, first: Boolean): String {
        var result = parse
        if (result.contains("&&")) {
            val parses = result.split("&&".toRegex()).toTypedArray()
            val new_parses = ArrayList<String>()
            for (i in parses.indices) {
                val pss = parses[i].split(" ".toRegex()).toTypedArray()
                val ps = pss[pss.size - 1]
                val m = NOADD_INDEX.matcher(ps)
                if (!m.find()) {
                    if (!first && i >= parses.size - 1) {
                        new_parses.add(parses[i])
                    } else {
                        new_parses.add(parses[i] + ":eq(0)")
                    }
                } else {
                    new_parses.add(parses[i])
                }
            }
            result = TextUtils.join(" ", new_parses)
        } else {
            val pss = result.split(" ".toRegex()).toTypedArray()
            val ps = pss[pss.size - 1]
            val m = NOADD_INDEX.matcher(ps)
            if (!m.find() && first) {
                result = "$result:eq(0)"
            }
        }
        return result
    }

    @JvmStatic
    fun parseDomForUrl(html: String, rule: String, add_url: String): String {
        var currentRule = rule
        if (pdfh_html != html) {
            pdfh_html = html
            pdfh_doc = Jsoup.parse(html)
        }
        val doc = pdfh_doc!!
        if (currentRule == "body&&Text" || currentRule == "Text") {
            return doc.text()
        } else if (currentRule == "body&&Html" || currentRule == "Html") {
            return doc.html()
        }
        var option = ""
        if (currentRule.contains("&&")) {
            val rs = currentRule.split("&&".toRegex()).toTypedArray()
            option = rs[rs.size - 1]
            val excludes = ArrayList(Arrays.asList(*rs))
            excludes.removeAt(rs.size - 1)
            currentRule = TextUtils.join("&&", excludes)
        }
        currentRule = parseHikerToJq(currentRule, true)
        val parses = currentRule.split(" ".toRegex()).toTypedArray()
        var ret = Elements()
        for (nparse in parses) {
            ret = parseOneRule(doc, nparse, ret)
            if (ret.isEmpty()) {
                return ""
            }
        }
        var result: String
        if (StringUtils.isNotEmpty(option)) {
            if (option == "Text") {
                result = ret.text()
            } else if (option == "Html") {
                result = ret.html()
            } else {
                result = ret.attr(option)
                if (option.lowercase().contains("style") && result.contains("url(")) {
                    val m = p.matcher(result)
                    if (m.find()) {
                        result = m.group(1) ?: ""
                    }
                    if (StringUtils.isNotEmpty(result)) {
                        result = result.replace("^['|\"](.*)['|\"]$".toRegex(), "$1")
                    }
                }
                if (StringUtils.isNotEmpty(result) && StringUtils.isNotEmpty(add_url)) {
                    val m = URLJOIN_ATTR.matcher(option)
                    val n = SPECIAL_URL.matcher(result)
                    if (m.find() && !n.find()) {
                        result = if (result.contains("http")) {
                            result.substring(result.indexOf("http"))
                        } else {
                            joinUrl(add_url, result)
                        }
                    }
                }
            }
        } else {
            result = ret.outerHtml()
        }
        return result
    }

    @JvmStatic
    fun parseDomForArray(html: String, rule: String): List<String> {
        if (pdfa_html != html) {
            pdfa_html = html
            pdfa_doc = Jsoup.parse(html)
        }
        val doc = pdfa_doc!!
        val processedRule = parseHikerToJq(rule, false)
        val parses = processedRule.split(" ".toRegex()).toTypedArray()
        var ret = Elements()
        for (pars in parses) {
            ret = parseOneRule(doc, pars, ret)
            if (ret.isEmpty()) {
                return ArrayList()
            }
        }

        val eleHtml = ArrayList<String>()
        for (i in 0 until ret.size) {
            val element1: Element = ret[i]
            eleHtml.add(element1.outerHtml())
        }
        return eleHtml
    }

    private fun parseOneRule(doc: Document, nparse: String, currentRet: Elements): Elements {
        var ret = currentRet
        val painfo = getParseInfo(nparse)
        ret = if (ret.isEmpty()) {
            doc.select(painfo.nparse_rule!!)
        } else {
            ret.select(painfo.nparse_rule!!)
        }

        if (nparse.contains(":eq")) {
            ret = if (painfo.nparse_index < 0) {
                ret.eq(ret.size + painfo.nparse_index)
            } else {
                ret.eq(painfo.nparse_index)
            }
        }

        if (painfo.excludes != null && !ret.isEmpty()) {
            ret = ret.clone()
            for (i in painfo.excludes!!.indices) {
                ret.select(painfo.excludes!![i]).remove()
            }
        }
        return ret
    }

    @JvmStatic
    fun parseDomForList(html: String, p1: String, list_text: String, list_url: String, add_url: String): List<String> {
        if (pdfa_html != html) {
            pdfa_html = html
            pdfa_doc = Jsoup.parse(html)
        }
        val doc = pdfa_doc!!
        val processedP1 = parseHikerToJq(p1, false)
        val parses = processedP1.split(" ".toRegex()).toTypedArray()
        var ret = Elements()
        for (pars in parses) {
            ret = parseOneRule(doc, pars, ret)
            if (ret.isEmpty()) {
                return ArrayList()
            }
        }
        val new_vod_list = ArrayList<String>()
        for (i in 0 until ret.size) {
            val it = ret[i].outerHtml()
            new_vod_list.add(parseDomForUrl(it, list_text, "").trim() + '$' + parseDomForUrl(it, list_url, add_url))
        }
        return new_vod_list
    }
}
