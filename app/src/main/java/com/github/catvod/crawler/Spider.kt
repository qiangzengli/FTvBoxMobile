package com.github.catvod.crawler

import android.content.Context
import com.github.catvod.net.OkHttp
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.HashMap

open class Spider {

    @JvmField
    var siteKey: String? = null

    companion object {
        @JvmField
        var empty: JSONObject = JSONObject()

        @JvmStatic
        var mContext: Context? = null

        @JvmStatic
        fun safeDns(): Dns = OkHttp.dns()

        @JvmStatic
        fun client(): OkHttpClient = OkHttp.client()
    }

    open fun init(context: Context?) {
        mContext = context
    }

    open fun init(context: Context?, extend: String?) {
        init(context)
    }

    open fun initApi(api: SpiderApi?) {}

    /**
     * 首页数据内容
     *
     * @param filter 是否开启筛选
     * @return
     */
    open fun homeContent(filter: Boolean): String = ""

    /**
     * 首页最近更新数据 如果上面的homeContent中不包含首页最近更新视频的数据 可以使用这个接口返回
     *
     * @return
     */
    open fun homeVideoContent(): String = ""

    /**
     * 分类数据
     *
     * @param tid
     * @param pg
     * @param filter
     * @param extend
     * @return
     */
    open fun categoryContent(tid: String?, pg: String?, filter: Boolean, extend: HashMap<String, String>?): String = ""

    /**
     * 详情数据
     *
     * @param ids
     * @return
     */
    open fun detailContent(ids: List<String?>?): String = ""

    /**
     * 搜索数据内容
     *
     * @param key
     * @param quick
     * @return
     */
    open fun searchContent(key: String?, quick: Boolean): String = ""

    open fun searchContent(key: String?, quick: Boolean, pg: String?): String = searchContent(key, quick)

    /**
     * 播放信息
     *
     * @param flag
     * @param id
     * @return
     */
    open fun playerContent(flag: String?, id: String?, vipFlags: List<String?>?): String = ""

    /**
     * webview解析时使用 可自定义判断当前加载的 url 是否是视频
     *
     * @param url
     * @return
     */
    open fun isVideoFormat(url: String?): Boolean = false

    /**
     * 是否手动检测webview中加载的url
     *
     * @return
     */
    open fun manualVideoCheck(): Boolean = false

    /**
     * 直播list
     * @return
     */
    open fun liveContent(url: String?): String = ""

    /**
     * 取消请求tag
     */
    open fun cancelByTag() {}

    /**
     * 销毁
     */
    open fun destroy() {}

    /**
     * 爬虫代理
     * @param params
     * @return
     */
    open fun proxyLocal(params: Map<String, String>?): Array<Any?>? = null

    open fun proxy(params: Map<String, String>?): Array<Any?>? = proxyLocal(params)

    open fun action(action: String?): String? = null
}
