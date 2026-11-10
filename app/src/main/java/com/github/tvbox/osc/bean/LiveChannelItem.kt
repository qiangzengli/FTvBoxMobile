package com.github.tvbox.osc.bean

import com.google.gson.JsonObject
import java.util.ArrayList
import java.util.HashMap
import java.util.Objects

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
class LiveChannelItem {
    /**
     * channelIndex : 频道索引号
     * channelNum : 频道名称
     * channelSourceNames : 频道源名称
     * channelUrls : 频道源地址
     * sourceIndex : 频道源索引
     * sourceNum : 频道源总数
     */
    private var channelIndex: Int = 0
    private var channelNum: Int = 0
    private var channelName: String? = null
    private var channelLogo: String? = null
    private var channelEpg: String? = null
    private var channelUa: String? = null
    private var channelClick: String? = null
    private var channelFormat: String? = null
    private var channelOrigin: String? = null
    private var channelReferer: String? = null
    private var channelTvgId: String? = null
    private var channelTvgName: String? = null
    private var channelCatchup: JsonObject? = null
    private var channelHeader: MutableMap<String, String>? = null
    private var channelParse: Int? = null
    private var channelSourceNames: ArrayList<String>? = null
    private var channelUrls: ArrayList<String>? = null

    @JvmField
    var sourceIndex: Int = 0

    @JvmField
    var sourceNum: Int = 0

    @JvmField
    var include_back: Boolean = false

    fun setinclude_back(include_back: Boolean) {
        this.include_back = include_back
    }

    fun getinclude_back(): Boolean {
        return include_back
    }

    fun setChannelIndex(channelIndex: Int) {
        this.channelIndex = channelIndex
    }

    fun getChannelIndex(): Int {
        return channelIndex
    }

    fun setChannelNum(channelNum: Int) {
        this.channelNum = channelNum
    }

    fun getChannelNum(): Int {
        return channelNum
    }

    fun setChannelName(channelName: String?) {
        this.channelName = channelName
    }

    fun getChannelName(): String? {
        return channelName
    }

    fun setChannelLogo(channelLogo: String?) {
        this.channelLogo = channelLogo
    }

    fun getChannelLogo(): String {
        return channelLogo ?: ""
    }

    fun setChannelEpg(channelEpg: String?) {
        this.channelEpg = channelEpg
    }

    fun getChannelEpg(): String {
        return channelEpg ?: ""
    }

    fun setChannelUa(channelUa: String?) {
        this.channelUa = channelUa
    }

    fun getChannelUa(): String {
        return channelUa ?: ""
    }

    fun setChannelClick(channelClick: String?) {
        this.channelClick = channelClick
    }

    fun getChannelClick(): String {
        return channelClick ?: ""
    }

    fun setChannelFormat(channelFormat: String?) {
        this.channelFormat = channelFormat
    }

    fun getChannelFormat(): String {
        return channelFormat ?: ""
    }

    fun setChannelOrigin(channelOrigin: String?) {
        this.channelOrigin = channelOrigin
    }

    fun getChannelOrigin(): String {
        return channelOrigin ?: ""
    }

    fun setChannelReferer(channelReferer: String?) {
        this.channelReferer = channelReferer
    }

    fun getChannelReferer(): String {
        return channelReferer ?: ""
    }

    fun setChannelTvgId(channelTvgId: String?) {
        this.channelTvgId = channelTvgId
    }

    fun getChannelTvgId(): String {
        return channelTvgId ?: ""
    }

    fun setChannelTvgName(channelTvgName: String?) {
        this.channelTvgName = channelTvgName
    }

    fun getChannelTvgName(): String {
        return channelTvgName ?: ""
    }

    fun setChannelCatchup(channelCatchup: JsonObject?) {
        this.channelCatchup = channelCatchup
    }

    fun getChannelCatchup(): JsonObject {
        return channelCatchup ?: JsonObject()
    }

    fun hasCatchup(): Boolean {
        return channelCatchup != null && channelCatchup!!.entrySet().size > 0
    }

    fun setChannelHeader(channelHeader: MutableMap<String, String>?) {
        this.channelHeader = channelHeader
    }

    fun getChannelHeader(): MutableMap<String, String> {
        return channelHeader ?: HashMap()
    }

    fun setChannelParse(channelParse: Int?) {
        this.channelParse = channelParse
    }

    fun getChannelParse(): Int {
        return channelParse ?: 0
    }

    fun getHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>(getChannelHeader())
        if (getChannelUa().isNotEmpty()) headers["User-Agent"] = getChannelUa()
        if (getChannelOrigin().isNotEmpty()) headers["Origin"] = getChannelOrigin()
        if (getChannelReferer().isNotEmpty()) headers["Referer"] = getChannelReferer()
        return headers
    }

    fun getChannelUrls(): ArrayList<String>? {
        return channelUrls
    }

    fun setChannelUrls(channelUrls: ArrayList<String>?) {
        this.channelUrls = channelUrls
        sourceNum = channelUrls?.size ?: 0
    }

    fun preSource() {
        sourceIndex--
        if (sourceIndex < 0) sourceIndex = sourceNum - 1
    }

    fun nextSource() {
        sourceIndex++
        if (sourceIndex == sourceNum) sourceIndex = 0
    }

    fun setSourceIndex(sourceIndex: Int) {
        this.sourceIndex = sourceIndex
    }

    fun getSourceIndex(): Int {
        return sourceIndex
    }

    fun getUrl(): String {
        return channelUrls?.get(sourceIndex) ?: ""
    }

    fun getSourceNum(): Int {
        return sourceNum
    }

    fun getChannelSourceNames(): ArrayList<String>? {
        return channelSourceNames
    }

    fun setChannelSourceNames(channelSourceNames: ArrayList<String>?) {
        this.channelSourceNames = channelSourceNames
    }

    fun getSourceName(): String {
        return channelSourceNames?.get(sourceIndex) ?: ""
    }

    fun isEmptyCatchup(): Boolean {
        return channelCatchup == null || channelCatchup!!.entrySet().size == 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as LiveChannelItem
        return Objects.equals(channelName, that.channelName) &&
                Objects.equals(channelUrls?.get(sourceIndex), that.getUrl())
    }

    override fun hashCode(): Int {
        return Objects.hash(channelName, channelUrls?.get(sourceIndex))
    }
}
