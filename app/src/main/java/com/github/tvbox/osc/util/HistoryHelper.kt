package com.github.tvbox.osc.util

import com.orhanobut.hawk.Hawk
import java.util.ArrayList

object HistoryHelper {
    private val hisNumArray = arrayOf(30, 50, 100)
    private const val API_LINE_SPLIT = "\t"

    @JvmStatic
    fun getHistoryNumName(index: Int): String {
        val value = getHisNum(index)
        return value.toString() + "条"
    }

    @JvmStatic
    fun getHisNum(index: Int): Int {
        return if (index in hisNumArray.indices) {
            hisNumArray[index]
        } else {
            hisNumArray[0]
        }
    }

    @JvmStatic
    fun setSearchHistory(title: String) {
        val history = Hawk.get(HawkConfig.SEARCH_HISTORY, ArrayList<String>())
        history.remove(title)
        history.add(0, title)
        if (history.size > 30) {
            history.removeAt(history.size - 1)
        }
        Hawk.put(HawkConfig.SEARCH_HISTORY, history)
    }

    @JvmStatic
    fun clearSearchHistory() {
        Hawk.put(HawkConfig.SEARCH_HISTORY, ArrayList<String>())
    }

    @JvmStatic
    fun setLiveApiHistory(value: String) {
        val history = Hawk.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
        if (!history.contains(value)) {
            history.add(0, value)
        }
        if (history.size > 30) {
            history.removeAt(30)
        }
        Hawk.put(HawkConfig.LIVE_API_HISTORY, history)
    }

    @JvmStatic
    fun setApiHistory(value: String) {
        val history = Hawk.get(HawkConfig.API_HISTORY, ArrayList<String>())
        if (!history.contains(value)) {
            history.add(0, value)
        }
        if (history.size > 30) {
            history.removeAt(30)
        }
        Hawk.put(HawkConfig.API_HISTORY, history)
    }

    @JvmStatic
    fun buildApiLine(name: String?, url: String?): String {
        var lineName = name?.trim() ?: ""
        val lineUrl = url?.trim() ?: ""
        if (lineName.isEmpty()) {
            lineName = lineUrl
        }
        return lineName + API_LINE_SPLIT + lineUrl
    }

    @JvmStatic
    fun getApiLineName(value: String?): String {
        if (value == null) return ""
        val splitIndex = value.indexOf(API_LINE_SPLIT)
        val name = if (splitIndex >= 0) value.substring(0, splitIndex) else value
        return name.trim()
    }

    @JvmStatic
    fun getApiLineUrl(value: String?): String {
        if (value == null) return ""
        val splitIndex = value.indexOf(API_LINE_SPLIT)
        val url = if (splitIndex >= 0) value.substring(splitIndex + API_LINE_SPLIT.length) else value
        return url.trim()
    }

    @JvmStatic
    fun isApiLineUrl(url: String?): Boolean {
        if (url.isNullOrEmpty() || url.trim().isEmpty()) return false
        val trimUrl = url.trim()
        val apiLines = Hawk.get(HawkConfig.API_LINE_LIST, ArrayList<String>())
        for (apiLine in apiLines) {
            if (trimUrl == getApiLineUrl(apiLine)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isApiLineSource(url: String?): Boolean {
        if (url.isNullOrEmpty() || url.trim().isEmpty()) return false
        val source = Hawk.get(HawkConfig.API_LINE_SOURCE, "")
        return url.trim() == source
    }

    @JvmStatic
    fun isApiLineHistory(url: String?): Boolean {
        return isApiLineSource(url) || isApiLineUrl(url)
    }

    @JvmStatic
    fun clearApiLineList() {
        Hawk.put(HawkConfig.API_LINE_LIST, ArrayList<String>())
        Hawk.put(HawkConfig.API_LINE_SOURCE, "")
    }
}
