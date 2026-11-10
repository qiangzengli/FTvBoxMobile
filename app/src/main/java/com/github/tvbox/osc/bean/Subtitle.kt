package com.github.tvbox.osc.bean

class Subtitle {

    private var name: String? = null
    private var url: String? = null
    private var isZip: Boolean = false

    fun getIsZip(): Boolean {
        return isZip
    }

    fun getName(): String? {
        return name
    }

    fun getUrl(): String? {
        return url
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun setUrl(url: String?) {
        this.url = url
    }

    fun setIsZip(zip: Boolean) {
        isZip = zip
    }

    override fun toString(): String {
        return "Subtitle{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", isZip=" + isZip +
                '}'
    }
}
