package com.github.tvbox.osc.util

import android.net.Uri
import com.github.tvbox.osc.util.RegexUtils.getPattern
import java.util.ArrayList
import java.util.HashMap
import java.util.regex.Pattern

object VideoParseRuler {
    private val HOSTS_RULE = HashMap<String, ArrayList<ArrayList<String>>>()
    private val HOSTS_FILTER = HashMap<String, ArrayList<ArrayList<String>>>()
    private val HOSTS_REGEX = HashMap<String, ArrayList<String>>()
    private val HOSTS_SCRIPT = HashMap<String, ArrayList<String>>()

    @JvmStatic
    fun clearRule() {
        HOSTS_RULE.clear()
        HOSTS_FILTER.clear()
        HOSTS_REGEX.clear()
        HOSTS_SCRIPT.clear()
    }

    @JvmStatic
    fun addHostRule(host: String, rule: ArrayList<String>) {
        var rules = ArrayList<ArrayList<String>>()
        if (HOSTS_RULE[host] != null && HOSTS_RULE[host]!!.size > 0) {
            rules = HOSTS_RULE[host]!!
        }
        rules.add(rule)
        HOSTS_RULE[host] = rules
    }

    @JvmStatic
    fun getHostRules(host: String?): ArrayList<ArrayList<String>>? {
        if (host != null && HOSTS_RULE.containsKey(host)) {
            return HOSTS_RULE[host]
        }
        return null
    }

    @JvmStatic
    fun addHostFilter(host: String, rule: ArrayList<String>) {
        var filters = ArrayList<ArrayList<String>>()
        if (HOSTS_FILTER[host] != null && HOSTS_FILTER[host]!!.size > 0) {
            filters = HOSTS_FILTER[host]!!
        }
        filters.add(rule)
        HOSTS_FILTER[host] = filters
    }

    @JvmStatic
    fun getHostFilters(host: String?): ArrayList<ArrayList<String>>? {
        if (host != null && HOSTS_FILTER.containsKey(host)) {
            return HOSTS_FILTER[host]
        }
        return null
    }

    @JvmStatic
    fun addHostRegex(host: String, regex: ArrayList<String>?) {
        if (regex == null || regex.isEmpty()) return
        var temp = ArrayList<String>()
        if (HOSTS_REGEX[host] != null && HOSTS_REGEX[host]!!.isNotEmpty()) temp = HOSTS_REGEX[host]!!
        temp.addAll(regex)
        HOSTS_REGEX[host] = temp
    }

    @JvmStatic
    fun getHostsRegex(): HashMap<String, ArrayList<String>> {
        return HOSTS_REGEX
    }

    @JvmStatic
    fun checkIsVideoForParse(webUrl: String?, url: String): Boolean {
        try {
            var isVideo = DefaultConfig.isVideoFormat(url)
            if (HOSTS_RULE.isNotEmpty() && !isVideo && webUrl != null) {
                val uri = Uri.parse(webUrl)
                val host = uri.host
                isVideo = if (host != null && getHostRules(host) != null) {
                    checkVideoForOneHostRules(host, url)
                } else {
                    checkVideoForOneHostRules("*", url)
                }
            }
            return isVideo
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun checkVideoForOneHostRules(host: String, url: String): Boolean {
        var isVideo = false
        val hostRules = getHostRules(host)
        if (hostRules != null && hostRules.isNotEmpty()) {
            var isVideoRuleCheck = false
            for (i in hostRules.indices) {
                var checkIsVideo = true
                val ruleGroup = hostRules[i]
                if (ruleGroup != null && ruleGroup.isNotEmpty()) {
                    for (j in ruleGroup.indices) {
                        val onePattern: Pattern = getPattern("" + ruleGroup[j])
                        if (!onePattern.matcher(url).find()) {
                            checkIsVideo = false
                            break
                        }
                        LOG.i("echo-VIDEO RULE:" + ruleGroup[j])
                    }
                } else {
                    checkIsVideo = false
                }
                if (checkIsVideo) {
                    isVideoRuleCheck = true
                    break
                }
            }
            if (isVideoRuleCheck) {
                isVideo = true
            }
        }
        return isVideo
    }

    @JvmStatic
    fun isFilter(webUrl: String?, url: String): Boolean {
        try {
            var isFilter = false
            if (HOSTS_FILTER.isNotEmpty() && webUrl != null) {
                val uri = Uri.parse(webUrl)
                val host = uri.host
                if (host != null && getHostFilters(host) != null) {
                    isFilter = checkIsFilterForOneHostRules(host, url)
                }
            }
            return isFilter
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun checkIsFilterForOneHostRules(host: String, url: String): Boolean {
        var isFilter = false
        val hostFilters = getHostFilters(host)
        if (hostFilters != null && hostFilters.isNotEmpty()) {
            var isFilterRuleCheck = false
            for (i in hostFilters.indices) {
                var checkIsFilter = true
                val filterGroup = hostFilters[i]
                if (filterGroup != null && filterGroup.isNotEmpty()) {
                    for (j in filterGroup.indices) {
                        val onePattern: Pattern = getPattern("" + filterGroup[j])
                        if (!onePattern.matcher(url).find()) {
                            checkIsFilter = false
                            break
                        }
                        LOG.i("echo-FILTER RULE:" + filterGroup[j])
                    }
                } else {
                    checkIsFilter = false
                }
                if (checkIsFilter) {
                    isFilterRuleCheck = true
                    break
                }
            }
            if (isFilterRuleCheck) {
                isFilter = true
            }
        }
        return isFilter
    }

    @JvmStatic
    fun addHostScript(host: String, script: ArrayList<String>?) {
        if (script == null || script.isEmpty()) return
        var temp = ArrayList<String>()
        if (HOSTS_SCRIPT[host] != null && HOSTS_SCRIPT[host]!!.isNotEmpty()) temp = HOSTS_SCRIPT[host]!!
        temp.addAll(script)
        HOSTS_SCRIPT[host] = temp
    }

    @JvmStatic
    fun getHostScript(url: String): String {
        for ((host, list) in HOSTS_SCRIPT) {
            if (url.contains(host)) {
                if (list.isNotEmpty()) {
                    return list[0]
                }
            }
        }
        return ""
    }
}
