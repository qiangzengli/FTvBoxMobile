package com.github.tvbox.osc.util

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
object HawkConfig {
    const val API_URL = "api_url"
    const val EPG_URL = "epg_url"
    const val SHOW_PREVIEW = "show_preview"
    const val API_HISTORY = "api_history"
    const val API_LINE_LIST = "api_line_list"
    const val API_LINE_SOURCE = "api_line_source"
    const val LIVE_API_HISTORY = "live_api_history"
    const val EPG_HISTORY = "epg_history"
    const val HOME_API = "home_api"
    const val DEFAULT_PARSE = "parse_default"
    const val DEBUG_OPEN = "debug_open"
    const val PARSE_WEBVIEW = "parse_webview" // true 系统 false xwalk
    const val IJK_CODEC = "ijk_codec"
    const val PLAY_TYPE = "play_type" // 1 ijk 2 exo 10 MXPlayer
    const val LIVE_PLAY_TYPE = "live_play_type" // 1 ijk 2 exo 10 MXPlayer
    const val PLAY_RENDER = "play_render" // 0 texture 2
    const val PLAY_SCALE = "play_scale" // 0 texture 2
    const val LIVE_PLAY_SCALE = "live_play_scale"
    const val PLAY_TIME_STEP = "play_time_step" // 0 texture 2
    const val DOH_URL = "doh_url"
    const val DEFAULT_HOME_REC = 1
    const val HOME_REC = "home_rec" // 0 豆瓣热播 1 数据源推荐 2 历史
    const val HISTORY_NUM = "history_num"
    const val HISTORY_MERGE = "history_merge"
    const val SEARCH_VIEW = "search_view" // 0 列表 1 缩略图
    const val LIVE_CHANNEL = "last_live_channel_name"
    const val LIVE_CHANNEL_REVERSE = "live_channel_reverse"
    const val LIVE_CROSS_GROUP = "live_cross_group"
    const val LIVE_CONNECT_TIMEOUT = "live_connect_timeout"
    const val LIVE_SHOW_NET_SPEED = "live_show_net_speed"
    const val LIVE_SHOW_RESOLUTION = "live_show_resolution"
    const val LIVE_SHOW_TIME = "live_show_time"
    const val FAST_SEARCH_MODE = "fast_search_mode"
    const val SUBTITLE_TEXT_SIZE = "subtitle_text_size"
    const val SUBTITLE_TIME_DELAY = "subtitle_time_delay"
    const val SUBTITLE_EXO_SCALE = "subtitle_exo_scale"
    const val SUBTITLE_EXO_POSITION = "subtitle_exo_position"
    const val SOURCES_FOR_SEARCH = "checked_sources_for_search"
    const val HOME_REC_STYLE = "home_rec_style"
    const val NOW_DATE = "now_date" // 当前日期
    const val REMOTE_TVBOX = "remote_tvbox_host"
    const val IJK_CACHE_PLAY = "ijk_cache_play"
    const val PLAYER_IS_LIVE = "player_is_live"
    const val DOH_JSON = "doh_json"
    const val LIVE_GROUP_INDEX = "live_group_index"
    const val LIVE_GROUP_LIST = "live_group_list"
    const val LIVE_API_URL = "live_api_url"
    const val M3U8_PURIFY = "m3u8_purify"
    const val AUTO_SWITCH_LINE = "auto_switch_line"
    const val SCREEN_DISPLAY = "screen_display"
    const val LIVE_WEB_HEADER = "live_web_header"
    const val DEFAULT_LOAD_LIVE = "DEFAULT_LOAD_LIVE"
    const val SEARCH_HISTORY = "search_history"
    const val DANMU_OPEN = "danmu_open"
    const val DANMU_MAX_LINE = "danmu_max_line"
    const val DANMU_SPEED = "danmu_speed"
    const val DANMU_ALPHA = "danmu_alpha"
    const val DANMU_SIZE_SCALE = "danmu_size_scale"
    const val DANMU_RANDOM_COLOR = "danmu_random_color"
    const val DANMU_API = "danmu_api"

    @JvmField
    var hotVodDelete: Boolean = false
}
