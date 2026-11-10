package com.github.tvbox.osc.bean

import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk
import java.util.LinkedHashMap

/**
 * @author pj567
 * @date :2021/3/8
 * @description:
 */
class IJKCode {
    private var name: String? = null
    private var option: LinkedHashMap<String, String>? = null
    private var selected: Boolean = false

    fun selected(selected: Boolean) {
        this.selected = selected
        if (selected) {
            Hawk.put(HawkConfig.IJK_CODEC, name)
        }
    }

    fun isSelected(): Boolean {
        return selected
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getOption(): LinkedHashMap<String, String>? {
        return option
    }

    fun setOption(option: LinkedHashMap<String, String>?) {
        this.option = option
    }
}
