package com.github.tvbox.osc.event

/**
 * @author pj567
 * @date :2021/1/6
 * @description:
 */
class RefreshEvent {
    @JvmField
    var type: Int = 0

    @JvmField
    var obj: Any? = null

    constructor(type: Int) {
        this.type = type
    }

    constructor(type: Int, obj: Any?) {
        this.type = type
        this.obj = obj
    }

    companion object {
        const val TYPE_REFRESH = 0
        const val TYPE_HISTORY_REFRESH = 1
        const val TYPE_QUICK_SEARCH = 2
        const val TYPE_QUICK_SEARCH_SELECT = 3
        const val TYPE_QUICK_SEARCH_WORD = 4
        const val TYPE_QUICK_SEARCH_WORD_CHANGE = 5
        const val TYPE_SEARCH_RESULT = 6
        const val TYPE_QUICK_SEARCH_RESULT = 7
        const val TYPE_API_URL_CHANGE = 8
        const val TYPE_PUSH_URL = 9
        const val TYPE_EPG_URL_CHANGE = 10
        const val TYPE_SETTING_SEARCH_TV = 11
        const val TYPE_SUBTITLE_SIZE_CHANGE = 12
        const val TYPE_FILTER_CHANGE = 13
        const val TYPE_LIVE_API_URL_CHANGE = 14
        const val TYPE_HOME_SOURCE_CHANGE = 15
        const val TYPE_SET_DANMU_SETTINGS = 18
        const val TYPE_DANMU_REFRESH = 19
        const val TYPE_PLAY_QUALITY = 20
        const val TYPE_CONFIG_AUTH_CHANGED = 21
    }
}
