@file:Suppress("ClassName")

package com.github.catvod.crawler.js

import androidx.annotation.Keep
import com.orhanobut.hawk.Hawk
import com.whl.quickjs.wrapper.Function

class local {

    @Keep
    @Function
    fun delete(str: String?, str2: String?) {
        try {
            Hawk.delete("jsRuntime_" + str + "_" + str2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Keep
    @Function
    fun get(str: String?, str2: String?): String {
        return try {
            Hawk.get("jsRuntime_" + str + "_" + str2, "")
        } catch (e: Exception) {
            Hawk.delete(str)
            str2 ?: ""
        }
    }

    @Keep
    @Function
    fun set(str: String?, str2: String?, str3: String?) {
        try {
            Hawk.put("jsRuntime_" + str + "_" + str2, str3)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
