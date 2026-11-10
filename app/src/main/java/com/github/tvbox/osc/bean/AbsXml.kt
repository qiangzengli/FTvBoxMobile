package com.github.tvbox.osc.bean

import com.thoughtworks.xstream.annotations.XStreamAlias
import java.io.Serializable

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("rss")
class AbsXml : Serializable {
    @JvmField
    var sourceKey: String? = null

    @JvmField
    var searchToken: String? = null

    @XStreamAlias("list")
    @JvmField
    var movie: Movie? = null

    @XStreamAlias("msg")
    @JvmField
    var msg: String? = null
}
