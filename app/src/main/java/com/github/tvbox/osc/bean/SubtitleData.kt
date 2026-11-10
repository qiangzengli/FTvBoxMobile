package com.github.tvbox.osc.bean

class SubtitleData {

    private var isNew: Boolean? = null
    private var subtitleList: MutableList<Subtitle>? = null
    private var isZip: Boolean? = null

    fun getIsNew(): Boolean? {
        return isNew
    }

    fun getSubtitleList(): MutableList<Subtitle>? {
        return subtitleList
    }

    fun getIsZip(): Boolean? {
        return isZip
    }

    fun setIsNew(isNew: Boolean?) {
        this.isNew = isNew
    }

    fun setSubtitleList(subtitle: MutableList<Subtitle>?) {
        this.subtitleList = subtitle
    }

    fun setIsZip(zip: Boolean?) {
        isZip = zip
    }

    override fun toString(): String {
        return "SubtitleData{" +
                "isNew='" + isNew + '\'' +
                '}'
    }
}
