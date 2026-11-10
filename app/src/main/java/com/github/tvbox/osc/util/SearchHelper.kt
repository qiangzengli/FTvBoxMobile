package com.github.tvbox.osc.util

import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.orhanobut.hawk.Hawk
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.regex.Pattern

object SearchHelper {
    private val SEASON_SUFFIX_PATTERN = Pattern.compile(
        "^(.*?)[\\s._-]*(?:(第\\s*)?([0-9]{1,2}|[零一二三四五六七八九十百千万两]+)(\\s*季)|([0-9]{1,2}))$"
    )

    private val CHINESE_NUMBERS = arrayOf(
        "零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"
    )

    @JvmStatic
    fun getSourcesForSearch(): HashMap<String, String>? {
        var mCheckSources: HashMap<String, String>?
        try {
            val api = Hawk.get(HawkConfig.API_URL, "")
            if (api.isEmpty()) return null
            val mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, HashMap<String, HashMap<String, String>>())
            mCheckSources = mCheckSourcesForApi[api]
        } catch (e: Exception) {
            return null
        }
        if (mCheckSources == null || mCheckSources.isEmpty()) {
            mCheckSources = getSources()
        }
        return mCheckSources
    }

    @JvmStatic
    fun putCheckedSources(mCheckSources: HashMap<String, String>?, isAll: Boolean) {
        val api = Hawk.get(HawkConfig.API_URL, "")
        if (api.isEmpty()) {
            return
        }
        var mCheckSourcesForApi = Hawk.get<HashMap<String, HashMap<String, String>>?>(HawkConfig.SOURCES_FOR_SEARCH, null)

        if (isAll) {
            if (mCheckSourcesForApi == null) return
            mCheckSourcesForApi.remove(api)
        } else {
            if (mCheckSourcesForApi == null) mCheckSourcesForApi = HashMap()
            if (mCheckSources != null) {
                mCheckSourcesForApi[api] = mCheckSources
            }
        }
        SearchActivity.setCheckedSourcesForSearch(mCheckSources)
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi)
    }

    @JvmStatic
    fun getSources(): HashMap<String, String> {
        val mCheckSources = HashMap<String, String>()
        val list = ApiConfig.get().getSourceBeanList()
        for (bean in list) {
            if (!bean.isSearchable()) {
                continue
            }
            mCheckSources[bean.key] = "1"
        }
        return mCheckSources
    }

    @JvmStatic
    fun splitWords(text: String?): MutableList<String> {
        val result = ArrayList<String>()
        if (text.isNullOrEmpty() || text.trim().isEmpty()) {
            return result
        }
        result.add(text)
        val parts = text.split("\\W+".toRegex()).toTypedArray()
        if (parts.size > 1) {
            result.addAll(listOf(*parts))
        }
        val matcher = SEASON_SUFFIX_PATTERN.matcher(text.trim())
        if (matcher.matches()) {
            val baseName = matcher.group(1)?.trim() ?: ""
            if (baseName.isNotEmpty() &&
                !Character.isDigit(baseName[baseName.length - 1]) &&
                !result.contains(baseName)
            ) {
                result.add(baseName)
            }
            val season = if (matcher.group(3) == null) matcher.group(5) else matcher.group(3)
            if (baseName.isNotEmpty() && season != null) {
                val nextSeason = nextSeason(season)
                if (nextSeason != null) {
                    val prefix = if (matcher.group(3) == null || matcher.group(2) == null) "" else matcher.group(2)
                    val suffix = if (matcher.group(3) == null || matcher.group(4) == null) "" else matcher.group(4)
                    val nextTitle = baseName + prefix + nextSeason + suffix
                    if (!result.contains(nextTitle)) {
                        result.add(nextTitle)
                    }
                }
            }
        }
        return result
    }

    private fun nextSeason(season: String): String? {
        val value = season.trim()
        try {
            return (value.toInt() + 1).toString()
        } catch (ignored: NumberFormatException) {
            if ("两" == value) {
                return "三"
            }
            for (i in 0 until CHINESE_NUMBERS.size - 1) {
                if (CHINESE_NUMBERS[i] == value) {
                    return CHINESE_NUMBERS[i + 1]
                }
            }
            return null
        }
    }
}
