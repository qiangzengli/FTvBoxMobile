package com.github.tvbox.osc.bean

import com.thoughtworks.xstream.annotations.XStreamAlias
import java.io.Serializable

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("rss")
class AbsSortXml : Serializable {
    @JvmField
    var sourceKey: String? = null

    @XStreamAlias("class")
    @JvmField
    var classes: MovieSort? = null

    @XStreamAlias("list")
    @JvmField
    var list: Movie? = null

    @JvmField
    var videoList: MutableList<Movie.Video>? = null
}
