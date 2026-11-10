package com.github.tvbox.osc.subtitle.model

class Subtitle {
    @JvmField
    var style: Style? = null

    @JvmField
    var region: Region? = null

    @JvmField
    var start: Time = Time("", "")

    @JvmField
    var end: Time = Time("", "")

    @JvmField
    var content: String = ""

    @JvmField
    var lines: MutableList<Subtitle>? = null

    @JvmField
    var lyricCurrent: Boolean = false
}
