package com.github.tvbox.osc.bean

import com.github.tvbox.osc.util.RegexUtils.getPattern
import java.io.Serializable
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
class VodInfo : Serializable {
    @JvmField
    var last: String? = null // 时间
    @JvmField
    var id: String? = null // 内容id
    @JvmField
    var tid: Int = 0 // 父级id
    @JvmField
    var name: String? = null // 影片名称 <![CDATA[老爸当家]]>
    @JvmField
    var type: String? = null // 类型名称
    @JvmField
    var dt: String? = null // 视频分类zuidam3u8,zuidall
    @JvmField
    var pic: String? = null // 图片
    @JvmField
    var lang: String? = null // 语言
    @JvmField
    var area: String? = null // 地区
    @JvmField
    var year: Int = 0 // 年份
    @JvmField
    var state: String? = null
    @JvmField
    var note: String? = null // 描述集数或者影片信息<![CDATA[共40集]]>
    @JvmField
    var actor: String? = null // 演员<![CDATA[张国立,蒋欣,高鑫,曹艳艳,王维维,韩丹彤,孟秀,王新]]>
    @JvmField
    var director: String? = null // 导演<![CDATA[陈国星]]>
    @JvmField
    var seriesFlags: ArrayList<VodSeriesFlag>? = null
    @JvmField
    var seriesMap: LinkedHashMap<String, MutableList<VodSeries>>? = null
    @JvmField
    var des: String? = null // <![CDATA[权来]
    @JvmField
    var playFlag: String? = null
    @JvmField
    var playIndex: Int = 0
    @JvmField
    var playNote: String = ""
    @JvmField
    var sourceKey: String? = null
    @JvmField
    var playerCfg: String = ""
    @JvmField
    var reverseSort: Boolean = false

    fun setVideo(video: Movie.Video) {
        last = video.last
        id = video.id
        tid = video.tid
        name = video.name
        type = video.type
        pic = video.pic
        lang = video.lang
        area = video.area
        year = video.year
        state = video.state
        note = video.note
        actor = video.actor
        director = video.director
        des = video.des
        if (video.urlBean != null && video.urlBean?.infoList != null && video.urlBean!!.infoList!!.isNotEmpty()) {
            val tempSeriesMap = LinkedHashMap<String, MutableList<VodSeries>>()
            val flags = ArrayList<VodSeriesFlag>()
            for (urlInfo in video.urlBean!!.infoList!!) {
                if (urlInfo.beanList != null && urlInfo.beanList!!.isNotEmpty()) {
                    val seriesList = ArrayList<VodSeries>()
                    for (infoBean in urlInfo.beanList!!) {
                        seriesList.add(VodSeries(infoBean.name, infoBean.url))
                    }
                    tempSeriesMap[urlInfo.flag ?: ""] = seriesList
                    flags.add(VodSeriesFlag(urlInfo.flag))
                }
            }

            seriesFlags = flags
            seriesMap = LinkedHashMap()
            for (flag in flags) {
                val list = tempSeriesMap[flag.name ?: ""]
                if (list != null) {
                    if (flags.size <= 5) {
                        if (isReverse(list)) Collections.reverse(list)
                    }
                    seriesMap!![flag.name ?: ""] = list
                }
            }
        }
    }

    private fun extractNumber(name: String?): Int {
        if (name == null) return 0
        val matcher = getPattern("\\d+").matcher(name)
        if (matcher.find()) {
            return matcher.group().toInt()
        }
        return 0
    }

    private fun isReverse(list: List<VodSeries>): Boolean {
        var ascCount = 0
        var descCount = 0
        val limit = Math.min(list.size - 1, 6)
        for (i in 0 until limit) {
            val current = extractNumber(list[i].name)
            val next = extractNumber(list[i + 1].name)
            if (current < next) {
                ascCount++
                if (ascCount == 2) return false
            } else if (current > next) {
                descCount++
                if (descCount == 2) return true
            }
        }
        return false
    }

    fun reverse() {
        val sMap = seriesMap ?: return
        for (flag in sMap.keys) {
            val list = sMap[flag]
            if (list != null) Collections.reverse(list)
        }
    }

    class VodSeriesFlag : Serializable {
        @JvmField
        var name: String? = null
        @JvmField
        var selected: Boolean = false

        constructor()

        constructor(name: String?) {
            this.name = name
        }
    }

    class VodSeries : Serializable {
        @JvmField
        var name: String? = null
        @JvmField
        var url: String? = null
        @JvmField
        var selected: Boolean = false

        constructor()

        constructor(name: String?, url: String?) {
            this.name = name
            this.url = url
        }
    }
}
