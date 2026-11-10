package com.github.tvbox.osc.bean

import android.text.TextUtils

class DanmuSearchResult(
    private val name: String?,
    private val url: String?,
    private val builtIn: Boolean
) {
    fun getName(): String {
        return if (TextUtils.isEmpty(name)) getUrl() else name!!
    }

    fun getUrl(): String {
        return url ?: ""
    }

    fun isBuiltIn(): Boolean {
        return builtIn
    }
}
