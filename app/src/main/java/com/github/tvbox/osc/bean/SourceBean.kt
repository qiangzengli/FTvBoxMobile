package com.github.tvbox.osc.bean

import java.util.ArrayList

class SourceBean {
    @JvmField
    var key: String = ""
    @JvmField
    var name: String = ""
    @JvmField
    var api: String = ""
    @JvmField
    var type: Int = 0 // 0 xml 1 json 3 Spider
    @JvmField
    var searchable: Int = 0 // 是否可搜索
    @JvmField
    var quickSearch: Int = 0 // 是否可以快速搜索
    @JvmField
    var changeable: Int = 1
    @JvmField
    var filterable: Int = 0 // 是否可以站点选择
    @JvmField
    var playerUrl: String = "" // 站点解析Url
    @JvmField
    var ext: String = "" // 扩展数据
    @JvmField
    var jar: String = "" // 自定义jar
    @JvmField
    var categories: ArrayList<String>? = null // 分类&排序
    @JvmField
    var playerType: Int = 0 // 1 ijk 2 exo 10 mxplayer -1 以参数设置页面的为准
    @JvmField
    var timeout: Int = 0 // 站点播放信息获取超时，单位秒
    @JvmField
    var clickSelector: String = "" // 需要点击播放的嗅探站点selector   ddrk.me;#id
    @JvmField
    var style: String = "" // 展示风格

    fun getKey(): String = key
    fun setKey(key: String?) { this.key = key ?: "" }

    fun getName(): String = name
    fun setName(name: String?) { this.name = name ?: "" }

    fun getApi(): String = api
    fun setApi(api: String?) { this.api = api ?: "" }

    fun getPlayerUrl(): String = playerUrl
    fun setPlayerUrl(playerUrl: String?) { this.playerUrl = playerUrl ?: "" }

    fun getType(): Int = type
    fun setType(type: Int) { this.type = type }

    fun isSearchable(): Boolean = searchable != 0
    fun setSearchable(searchable: Int) { this.searchable = searchable }

    fun isQuickSearch(): Boolean = quickSearch != 0
    fun setQuickSearch(quickSearch: Int) { this.quickSearch = quickSearch }

    fun isChangeable(): Boolean = changeable != 0
    fun setChangeable(changeable: Int) { this.changeable = changeable }

    fun getFilterable(): Int = filterable
    fun setFilterable(filterable: Int) { this.filterable = filterable }

    fun getExt(): String = ext
    fun setExt(ext: String?) { this.ext = ext ?: "" }

    fun getCategories(): ArrayList<String>? = categories
    fun setCategories(categories: ArrayList<String>?) { this.categories = categories }

    fun getJar(): String = jar
    fun setJar(jar: String?) { this.jar = jar ?: "" }

    fun getPlayerType(): Int = playerType
    fun setPlayerType(playerType: Int) { this.playerType = playerType }

    fun getTimeout(): Int = timeout
    fun setTimeout(timeout: Int) { this.timeout = timeout }

    fun getPlayTimeoutSeconds(): Int = if (timeout > 0) Math.max(5, Math.min(60, timeout)) else 15

    fun getClickSelector(): String = clickSelector
    fun setClickSelector(clickSelector: String?) { this.clickSelector = clickSelector ?: "" }

    fun getStyle(): String = style
    fun setStyle(style: String?) { this.style = style ?: "" }
}
