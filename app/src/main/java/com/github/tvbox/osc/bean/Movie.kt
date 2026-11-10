package com.github.tvbox.osc.bean

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamConverter
import com.thoughtworks.xstream.annotations.XStreamImplicit
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter
import java.io.Serializable

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("list")
class Movie : Serializable {
    @XStreamAsAttribute
    @JvmField
    var page: Int = 0

    @XStreamAsAttribute
    @JvmField
    var pagecount: Int = 0 // 总页数

    @XStreamAsAttribute
    @JvmField
    var pagesize: Int = 0

    @XStreamAsAttribute
    @JvmField
    var recordcount: Int = 0 // 总条数

    @XStreamImplicit(itemFieldName = "video")
    @JvmField
    var videoList: MutableList<Video>? = null

    @XStreamAlias("video")
    class Video : Serializable {
        @XStreamAlias("last") // 时间
        @JvmField
        var last: String? = null

        @XStreamAlias("id") // 内容id
        @JvmField
        var id: String? = null

        @XStreamAlias("tid") // 父级id
        @JvmField
        var tid: Int = 0

        @XStreamAlias("name") // 影片名称 <![CDATA[老爸当家]]>
        @JvmField
        var name: String? = null

        @XStreamAlias("type") // 类型名称
        @JvmField
        var type: String? = null

        @XStreamAlias("pic") // 图片
        @JvmField
        var pic: String? = null

        @XStreamAlias("lang") // 语言
        @JvmField
        var lang: String? = null

        @XStreamAlias("area") // 地区
        @JvmField
        var area: String? = null

        @XStreamAlias("year") // 年份
        @JvmField
        var year: Int = 0

        @XStreamAlias("state")
        @JvmField
        var state: String? = null

        @XStreamAlias("note") // 描述集数或者影片信息<![CDATA[共40集]]>
        @JvmField
        var note: String? = null

        @XStreamAlias("actor") // 演员<![CDATA[张国立,蒋欣,高鑫,曹艳艳,王维维,韩丹彤,孟秀,王新]]>
        @JvmField
        var actor: String? = null

        @XStreamAlias("director") // 导演<![CDATA[陈国星]]>
        @JvmField
        var director: String? = null

        @XStreamAlias("dl")
        @JvmField
        var urlBean: UrlBean? = null

        @XStreamAlias("des")
        @JvmField
        var des: String? = null // <![CDATA[权来]

        @JvmField
        var sourceKey: String? = null

        @XStreamAlias("tag")
        @JvmField
        var tag: String? = null

        @XStreamAlias("action")
        @JvmField
        var action: String? = null

        @XStreamAlias("dl")
        class UrlBean : Serializable {
            @XStreamImplicit(itemFieldName = "dd")
            @JvmField
            var infoList: MutableList<UrlInfo>? = null

            @XStreamAlias("dd")
            @XStreamConverter(value = ToAttributedValueConverter::class, strings = ["urls"])
            class UrlInfo : Serializable {
                @XStreamAsAttribute
                @JvmField
                var flag: String? = null // zuidam3u8,zuidall(MP4)

                @JvmField
                var urls: String? = null

                @JvmField
                var beanList: MutableList<InfoBean>? = null

                class InfoBean : Serializable {
                    @JvmField
                    var name: String? = null

                    @JvmField
                    var url: String? = null

                    constructor()

                    constructor(name: String?, url: String?) {
                        this.name = name
                        this.url = url
                    }
                }
            }
        }
    }
}
