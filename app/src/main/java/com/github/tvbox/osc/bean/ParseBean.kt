package com.github.tvbox.osc.bean

import com.github.tvbox.osc.util.DefaultConfig

/**
 * @author pj567
 * @date :2021/3/8
 * @description:
 */
class ParseBean {

    private var name: String? = null
    private var url: String? = null
    private var ext: String? = null
    private var type: Int = 0 // 0 普通嗅探 1 json 2 Json扩展 3 聚合
    private var isDefault: Boolean = false

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getUrl(): String? {
        return DefaultConfig.checkReplaceProxy(url)
    }

    fun setUrl(url: String?) {
        this.url = url
    }

    fun isDefault(): Boolean {
        return isDefault
    }

    fun setDefault(b: Boolean) {
        isDefault = b
    }

    fun getType(): Int {
        return type
    }

    fun setType(type: Int) {
        this.type = type
    }

    fun getExt(): String? {
        return ext
    }

    fun setExt(ext: String?) {
        this.ext = ext
    }

    fun mixUrl(): String {
        val currentExt = ext
        val currentUrl = url ?: ""
        if (!currentExt.isNullOrEmpty()) {
            val idx = currentUrl.indexOf("?")
            if (idx > 0) {
                return currentUrl.substring(0, idx + 1) + "cat_ext=" + android.util.Base64.encodeToString(currentExt.toByteArray(), android.util.Base64.DEFAULT or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP) + "&" + currentUrl.substring(idx + 1)
            }
        }
        return currentUrl
    }
}
