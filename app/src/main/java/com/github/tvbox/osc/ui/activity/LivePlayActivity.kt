package com.github.tvbox.osc.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.IntEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.Epginfo
import com.github.tvbox.osc.bean.LiveChannelGroup
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.bean.LiveDayListGroup
import com.github.tvbox.osc.bean.LiveEpgDate
import com.github.tvbox.osc.bean.LivePlayerManager
import com.github.tvbox.osc.bean.LiveSettingGroup
import com.github.tvbox.osc.bean.LiveSettingItem
import com.github.tvbox.osc.player.controller.LiveController
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter
import com.github.tvbox.osc.ui.adapter.MyEpgAdapter
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog
import com.github.tvbox.osc.ui.tv.widget.ViewObj
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.EpgUtil
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.github.tvbox.osc.util.live.TxtSubscribe
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils.safeTimeMs
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Hashtable
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
class LivePlayActivity : BaseActivity() {

    companion object {
        @JvmField
        var context: Context? = null

        @JvmField
        var currentChannelGroupIndex: Int = 0

        @JvmField
        var formatDate: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")

        @JvmField
        var formatDate1: SimpleDateFormat = SimpleDateFormat("MM-dd")

        @JvmField
        var day: String = formatDate.format(Date())

        @JvmField
        var nowday: Date = Date()

        @JvmField
        var playUrl: String? = null

        @JvmStatic
        fun getTime(startTime: String?, endTime: String?): Long {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            var eTime: Long = 0
            try {
                eTime = df.parse(endTime ?: "")?.time ?: 0
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            var sTime: Long = 0
            try {
                sTime = df.parse(startTime ?: "")?.time ?: 0
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            return (eTime - sTime) / 1000
        }
    }

    private var mVideoView: VideoView<AbstractPlayer>? = null
    private var switchChannelSnapshotOverlay: View? = null
    private var switchChannelSnapshotImage: ImageView? = null
    private var tvChannelInfo: TextView? = null
    private var tvTime: TextView? = null
    private var tvNetSpeed: TextView? = null
    private var tvTopStatus: View? = null
    private var tvResolution: TextView? = null
    private var tvLeftChannelListLayout: LinearLayout? = null
    private var mChannelGroupView: TvRecyclerView? = null
    private var mLiveChannelView: TvRecyclerView? = null
    private var liveChannelGroupAdapter: LiveChannelGroupAdapter? = null
    private var liveChannelItemAdapter: LiveChannelItemAdapter? = null

    private var tvRightSettingLayout: LinearLayout? = null
    private var mSettingGroupView: TvRecyclerView? = null
    private var mSettingItemView: TvRecyclerView? = null
    private var liveSettingGroupAdapter: LiveSettingGroupAdapter? = null
    private var liveSettingItemAdapter: LiveSettingItemAdapter? = null
    private var liveSettingGroupList: MutableList<LiveSettingGroup> = ArrayList()

    private val mHandler = Handler(Looper.getMainLooper())
    private var resolutionInfoRetryCount = 0
    private var resolutionInfoPending = false
    private var exitingLivePlay = false
    private val EPG_LOAD_DELAY = 1200L
    private val RESOLUTION_INFO_MAX_RETRY = 10
    private val RESOLUTION_INFO_RETRY_DELAY = 300L
    private val RESOLUTION_INFO_HIDE_DELAY = 3000L
    private val DEFAULT_EPG_ADDRESS = "http://epg.51zmt.top:8000/api/diyp/?ch={name}&date={date}"
    private val CATCHUP_TOKEN_PATTERN = Pattern.compile("(\\\$?\\{[^}]*\\})")
    private val CATCHUP_TAG_PATTERN = Pattern.compile("\\{([^}]*)\\}")

    private val mLoadEpgRun = Runnable {
        if (channel_Name != null && liveEpgDateAdapter != null && (liveEpgDateAdapter?.getSelectedIndex() ?: -1) >= 0) {
            getEpg(Date())
        }
    }
    private var firstLiveEpgLoad = true

    private var liveChannelGroupList: MutableList<LiveChannelGroup> = ArrayList()
    private var currentLiveChannelIndex = -1
    private var currentLiveLookBackIndex = -1
    private var currentLiveChangeSourceTimes = 0
    private var allowLiveSwitchPlayer = true
    private var currentLiveChannelItem: LiveChannelItem? = null
    private var pendingLiveRefreshChannelName: String? = null
    private var pendingLiveRefreshSourceIndex = -1
    private var refreshingLiveChannelList = false
    private var liveConfigRequestId = 0
    private val livePlayerManager = LivePlayerManager()
    private val channelGroupPasswordConfirmed = ArrayList<Int>()

    // EPG by 龍
    private var channel_Name: LiveChannelItem? = null
    private val hsEpg = Hashtable<String, ArrayList<Epginfo>>()
    private var countDownTimer: CountDownTimer? = null
    private var ll_right_top_loading: View? = null
    private var ll_right_top_huikan: View? = null
    private var divLoadEpg: View? = null
    private var divLoadEpgDivider: View? = null
    private var divLoadEpgleft: View? = null
    private var divEpg: LinearLayout? = null
    private var ll_epg: RelativeLayout? = null
    private var tv_channelnum: TextView? = null
    private var tip_chname: TextView? = null
    private var tip_epg1: TextView? = null
    private var tip_epg2: TextView? = null
    private var tv_srcinfo: TextView? = null
    private var tv_curepg_left: TextView? = null
    private var tv_nextepg_left: TextView? = null
    private var myAdapter: MyEpgAdapter? = null
    private var tv_right_top_tipnetspeed: TextView? = null
    private var tv_right_top_channel_name: TextView? = null
    private var tv_right_top_epg_name: TextView? = null
    private var tv_right_top_type: TextView? = null
    private var iv_circle_bg: ImageView? = null
    private var tv_shownum: TextView? = null
    private var txtNoEpg: TextView? = null
    private var iv_back_bg: ImageView? = null

    private var objectAnimator: ObjectAnimator? = null
    var epgStringAddress: String = ""

    private var mEpgDateGridView: TvRecyclerView? = null
    private var mRightEpgList: TvRecyclerView? = null
    private var liveEpgDateAdapter: LiveEpgDateAdapter? = null
    private var epgListAdapter: LiveEpgAdapter? = null

    private var liveDayList: MutableList<LiveDayListGroup> = ArrayList()

    private var isSHIYI = false
    private var isBack = false
    private var shiyi_time_c = 0 // 时移时间差值
    private var imgLiveIcon: ImageView? = null
    private var liveIconNullBg: FrameLayout? = null
    private var liveIconNullText: TextView? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd")
    private var backcontroller: View? = null
    private var countDownTimer3: CountDownTimer? = null
    private val videoWidth = 1920
    private val videoHeight = 1080
    private var tv_currentpos: TextView? = null
    private var tv_duration: TextView? = null
    private var sBar: SeekBar? = null
    private var iv_playpause: View? = null
    private var iv_play: View? = null
    private var show = false
    private val postTimeout = 6000

    // 遥控器数字键输入的要切换的频道号码
    private var selectedChannelNumber = 0
    private var tvSelectedChannel: TextView? = null

    override fun getLayoutResID(): Int {
        return R.layout.activity_live_play
    }

    override fun init() {
        context = this
        epgStringAddress = getConfiguredEpgAddress()

        setLoadSir(findViewById(R.id.live_root))
        mVideoView = findViewById(R.id.mVideoView)
        switchChannelSnapshotOverlay = findViewById(R.id.switchChannelSnapshotOverlay)
        switchChannelSnapshotImage = findViewById(R.id.switchChannelSnapshotImage)

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout)
        mChannelGroupView = findViewById(R.id.mGroupGridView)
        mLiveChannelView = findViewById(R.id.mChannelGridView)
        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout)
        mSettingGroupView = findViewById(R.id.mSettingGroupView)
        mSettingItemView = findViewById(R.id.mSettingItemView)
        tvChannelInfo = findViewById(R.id.tvChannel)
        tvTime = findViewById(R.id.tvTime)
        tvNetSpeed = findViewById(R.id.tvNetSpeed)
        tvTopStatus = findViewById(R.id.tv_top_status)
        tvResolution = findViewById(R.id.tvResolution)

        // EPG findViewById by 龍
        tip_chname = findViewById(R.id.tv_channel_bar_name) // 底部名称
        tv_channelnum = findViewById(R.id.tv_channel_bottom_number) // 底部数字
        tip_epg1 = findViewById(R.id.tv_current_program_time) // 底部EPG当前节目信息
        tip_epg2 = findViewById(R.id.tv_next_program_time) // 底部EPG当下个节目信息
        tv_srcinfo = findViewById(R.id.tv_source) // 线路状态
        tv_curepg_left = findViewById(R.id.tv_current_program) // 当前节目
        tv_nextepg_left = findViewById(R.id.tv_next_program) // 下一节目
        ll_epg = findViewById(R.id.ll_epg)
        tv_right_top_channel_name = findViewById(R.id.tv_right_top_channel_name)
        tv_right_top_epg_name = findViewById(R.id.tv_right_top_epg_name)
        iv_circle_bg = findViewById(R.id.iv_circle_bg)
        iv_back_bg = findViewById(R.id.iv_back_bg)
        tv_shownum = findViewById(R.id.tv_shownum)
        txtNoEpg = findViewById(R.id.txtNoEpg)
        ll_right_top_loading = findViewById(R.id.ll_right_top_loading)
        ll_right_top_huikan = findViewById(R.id.ll_right_top_huikan)
        divLoadEpg = findViewById(R.id.divLoadEpg)
        divLoadEpgDivider = findViewById(R.id.divLoadEpgDivider)
        divLoadEpgleft = findViewById(R.id.divLoadEpgleft)
        divEpg = findViewById(R.id.divEPG)

        // 右上角图片旋转
        objectAnimator = ObjectAnimator.ofFloat(iv_circle_bg, "rotation", 360.0f).apply {
            duration = postTimeout.toLong()
            repeatCount = -1
            start()
        }

        // laodao 7day replay
        mEpgDateGridView = findViewById(R.id.mEpgDateGridView)
        Hawk.put(HawkConfig.NOW_DATE, formatDate.format(Date()))
        day = formatDate.format(Date())
        nowday = Date()

        mRightEpgList = findViewById(R.id.lv_epg)
        // EPG频道名称
        imgLiveIcon = findViewById(R.id.img_live_icon)
        liveIconNullBg = findViewById(R.id.live_icon_null_bg)
        liveIconNullText = findViewById(R.id.live_icon_null_text)
        imgLiveIcon?.visibility = View.INVISIBLE
        liveIconNullText?.visibility = View.INVISIBLE
        liveIconNullBg?.visibility = View.INVISIBLE

        sBar = findViewById(R.id.pb_progressbar)
        tv_currentpos = findViewById(R.id.tv_currentpos)
        backcontroller = findViewById(R.id.backcontroller)
        tv_duration = findViewById(R.id.tv_duration)
        iv_playpause = findViewById(R.id.iv_playpause)
        iv_play = findViewById(R.id.iv_play)

        tvSelectedChannel = findViewById(R.id.tv_selected_channel)

        if (show) {
            backcontroller?.visibility = View.VISIBLE
            ll_epg?.visibility = View.GONE
        } else {
            backcontroller?.visibility = View.GONE
            ll_epg?.visibility = View.VISIBLE
        }

        iv_play?.setOnClickListener {
            mVideoView?.start()
            iv_play?.visibility = View.INVISIBLE
            countDownTimer?.start()
            iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
        }

        iv_playpause?.setOnClickListener {
            if (mVideoView?.isPlaying() == true) {
                mVideoView?.pause()
                countDownTimer?.cancel()
                iv_play?.visibility = View.VISIBLE
                iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.icon_play)
            } else {
                mVideoView?.start()
                iv_play?.visibility = View.INVISIBLE
                countDownTimer?.start()
                iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
            }
        }

        sBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(arg0: SeekBar?) {}
            override fun onStartTrackingTouch(arg0: SeekBar?) {}
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromuser: Boolean) {
                if (!fromuser) return
                if (countDownTimer != null) {
                    mVideoView?.seekTo(progress.toLong())
                    countDownTimer?.cancel()
                    countDownTimer?.start()
                }
            }
        })

        sBar?.setOnKeyListener { _, keycode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keycode == KeyEvent.KEYCODE_DPAD_CENTER || keycode == KeyEvent.KEYCODE_ENTER) {
                    if (mVideoView?.isPlaying() == true) {
                        mVideoView?.pause()
                        countDownTimer?.cancel()
                        iv_play?.visibility = View.VISIBLE
                        iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.icon_play)
                    } else {
                        mVideoView?.start()
                        iv_play?.visibility = View.INVISIBLE
                        countDownTimer?.start()
                        iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
                    }
                }
            }
            false
        }

        initEpgDateView()
        initEpgListView()
        initDayList()
        initVideoView()
        initChannelGroupView()
        initLiveChannelView()
        initSettingGroupView()
        initSettingItemView()
        initLiveChannelList()
        initLiveSettingGroupList()
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true)
        installPhoneControls()
    }

    private fun installPhoneControls() {
        val root = findViewById<FrameLayout>(R.id.live_root) ?: return
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        val labels = arrayOf("返回", "频道", "节目", "设置")
        for (i in labels.indices) {
            val action = i
            val button = com.fbox.mobile.MobileSheets.action(this, labels[i])
            button.setOnClickListener {
                when (action) {
                    0 -> onBackPressed()
                    1 -> showChannelList()
                    2 -> {
                        showChannelList()
                        findViewById<View>(R.id.divLoadEpg)?.performClick()
                    }
                    else -> showSettingGroup()
                }
            }
            actions.addView(button)
        }
        val params = FrameLayout.LayoutParams(-2, com.fbox.mobile.MobileSheets.dp(this, 48f), Gravity.TOP or Gravity.START)
        params.leftMargin = com.fbox.mobile.MobileSheets.dp(this, 12f)
        params.topMargin = com.fbox.mobile.MobileSheets.dp(this, 8f)
        root.addView(actions, params)
    }

    // 获取EPG并存储 // 百川epg DIYP epg 51zmt epg ------- 自建EPG格式输出格式请参考 51zmt
    private var epgdata: MutableList<Epginfo> = ArrayList()

    private fun showEpg(date: Date?, arrayList: ArrayList<Epginfo>?) {
        val hasEpg = arrayList != null && arrayList.isNotEmpty()
        updateEpgPanelState(hasEpg)
        if (hasEpg && arrayList != null) {
            epgdata = arrayList
            epgListAdapter?.CanBack(currentLiveChannelItem?.getinclude_back() ?: false)
            epgListAdapter?.setNewData(epgdata)
            updateCurrentEpgSelectedIndex()
        }
    }

    private fun findCurrentEpgIndex(epgList: List<Epginfo>?): Int {
        if (epgList.isNullOrEmpty()) return -1
        val now = Date()
        for (i in epgList.size - 1 downTo 0) {
            val epgInfo = epgList[i]
            val sDate = epgInfo.startdateTime
            val eDate = epgInfo.enddateTime
            if (sDate == null || eDate == null) {
                continue
            }
            var endDateTime = eDate
            if (!endDateTime.after(sDate)) {
                val calendar = Calendar.getInstance()
                calendar.time = endDateTime
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                endDateTime = calendar.time
            }
            if (!now.before(sDate) && now.before(endDateTime)) {
                return i
            }
        }
        return -1
    }

    private fun getCurrentEpgIndexOrSelected(): Int {
        val data = epgListAdapter?.data
        var epgIndex = findCurrentEpgIndex(data)
        if (epgIndex >= 0) return epgIndex
        epgIndex = epgListAdapter?.getSelectedIndex() ?: -1
        if (data != null && epgIndex >= 0 && epgIndex < data.size) return epgIndex
        return 0
    }

    private fun updateCurrentEpgSelectedIndex() {
        val data = epgListAdapter?.data ?: return
        if (data.isEmpty()) return
        val epgIndex = findCurrentEpgIndex(data)
        if (epgIndex >= 0) {
            epgListAdapter?.setSelectedEpgIndex(epgIndex)
        }
    }

    private fun syncCurrentEpgSelection(focus: Boolean) {
        val data = epgListAdapter?.data
        if (mRightEpgList == null || epgListAdapter == null || data.isNullOrEmpty()) return
        val epgIndex = getCurrentEpgIndexOrSelected()
        mRightEpgList?.setSelectedPosition(epgIndex)
        mRightEpgList?.setSelection(epgIndex)
        epgListAdapter?.setSelectedEpgIndex(epgIndex)
        if (focus) {
            epgListAdapter?.setFocusedEpgIndex(epgIndex)
            focusEpgPosition(epgIndex)
        } else {
            mRightEpgList?.post {
                mRightEpgList?.smoothScrollToPosition(epgIndex)
            }
        }
    }

    private fun updateEpgPanelState(hasEpg: Boolean) {
        if (hasEpg) {
            txtNoEpg?.visibility = View.GONE
            mRightEpgList?.visibility = View.VISIBLE
            divLoadEpgDivider?.visibility = View.VISIBLE
            if (divEpg?.visibility != View.VISIBLE) {
                divLoadEpg?.visibility = View.VISIBLE
                divLoadEpgleft?.visibility = View.GONE
            }
        } else {
            epgdata = ArrayList()
            epgListAdapter?.setNewData(epgdata)
            txtNoEpg?.visibility = View.GONE
            mRightEpgList?.visibility = View.GONE
            divEpg?.visibility = View.GONE
            divLoadEpg?.visibility = View.GONE
            divLoadEpgDivider?.visibility = View.GONE
            divLoadEpgleft?.visibility = View.GONE
            mChannelGroupView?.visibility = View.VISIBLE
        }
    }

    private fun getFirstPartBeforeSpace(str: String?): String? {
        if (str.isNullOrEmpty()) {
            return str
        }
        val spaceIndex = str.indexOf(' ')
        return if (spaceIndex == -1) {
            str
        } else {
            str.substring(0, spaceIndex)
        }
    }

    fun getEpg(date: Date) {
        val current = channel_Name ?: return
        val channelName = current.getChannelName() ?: return
        val channelNameReal = normalizeEpgChannelName(getFirstPartBeforeSpace(channelName))
        @SuppressLint("SimpleDateFormat") val timeFormat = SimpleDateFormat("yyyy-MM-dd")
        timeFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
        var epgTagName = channelNameReal
        val logo = logoUrl
        if (logo == null || logo.isEmpty()) {
            val epgInfo = EpgUtil.getEpgInfo(channelNameReal)
            if (epgInfo != null && !epgInfo[1].isEmpty()) {
                epgTagName = epgInfo[1]
            }
            updateChannelIcon(channelName, epgInfo?.get(0))
        } else if (logo == "false") {
            updateChannelIcon(channelName, null)
        } else {
            val formattedLogo = logo.replace("{name}", epgTagName)
            updateChannelIcon(channelName, formattedLogo)
        }
        val finalEpgTagName = epgTagName
        epgListAdapter?.CanBack(currentLiveChannelItem?.getinclude_back() ?: false)
        if (!hasEpgAddress()) {
            updateEpgPanelState(false)
            return
        }
        val epgQueryNames = buildEpgQueryNames(channelName, channelNameReal, finalEpgTagName)
        val url = buildEpgUrl(epgStringAddress, epgQueryNames[0], date, timeFormat)

        val selectedItem = liveEpgDateAdapter?.let { adapter ->
            val idx = adapter.getSelectedIndex()
            if (idx >= 0 && idx < adapter.data.size) adapter.getItem(idx) else null
        }
        val savedEpgKey = channelName + "_" + (selectedItem?.getDatePresented() ?: "")
        if (hsEpg.containsKey(savedEpgKey)) {
            showEpg(date, hsEpg[savedEpgKey])
            showBottomEpg()
            return
        }
        updateEpgPanelState(false)
        requestEpg(url, date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, 0)
    }

    private fun buildEpgUrl(address: String, epgTagName: String, date: Date, timeFormat: SimpleDateFormat): String {
        return if (address.contains("{name}") || address.contains("{date}")) {
            address.replace("{name}", encodeEpgParam(epgTagName)).replace("{date}", timeFormat.format(date))
        } else if (isXmlEpgAddress(address)) {
            address
        } else {
            address + (if (address.contains("?")) "&" else "?") + "ch=" + encodeEpgParam(epgTagName) + "&date=" + timeFormat.format(date)
        }
    }

    private fun encodeEpgParam(value: String?): String {
        return try {
            URLEncoder.encode(value ?: "", "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            value ?: ""
        }
    }

    private fun buildEpgQueryNames(channelName: String, channelNameReal: String, epgTagName: String): ArrayList<String> {
        val queryNames = ArrayList<String>()
        addEpgQueryName(queryNames, epgTagName)
        addEpgQueryName(queryNames, channelNameReal)
        addEpgQueryName(queryNames, normalizeEpgChannelName(getFirstPartBeforeSpace(channelName)))
        addEpgQueryName(queryNames, getFirstPartBeforeSpace(channelName))
        addEpgQueryName(queryNames, channelName)
        if (queryNames.isEmpty()) {
            queryNames.add("")
        }
        return queryNames
    }

    private fun addEpgQueryName(queryNames: ArrayList<String>, name: String?) {
        if (name == null) return
        val trimName = name.trim()
        if (trimName.isEmpty() || queryNames.contains(trimName)) return
        queryNames.add(trimName)
    }

    private fun getConfiguredEpgAddress(): String {
        val userEpgAddress: String? = Hawk.get(HawkConfig.EPG_URL, "")
        if (userEpgAddress != null && userEpgAddress.trim().length >= 5) {
            return userEpgAddress.trim()
        }
        return DEFAULT_EPG_ADDRESS
    }

    private fun hasEpgAddress(): Boolean {
        return epgStringAddress.trim().isNotEmpty()
    }

    private fun requestEpg(
        url: String,
        date: Date,
        channelNameReal: String,
        finalEpgTagName: String,
        savedEpgKey: String,
        epgQueryNames: ArrayList<String>,
        timeFormat: SimpleDateFormat,
        queryIndex: Int
    ) {
        var client: okhttp3.OkHttpClient? = OkGoHelper.getDefaultClient()
        if (client == null) client = com.github.catvod.net.OkHttp.client()
        client.newCall(okhttp3.Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                mHandler.post {
                    onEpgRequestFailure(date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.code() != 200) {
                    response.close()
                    mHandler.post {
                        onEpgRequestFailure(date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)
                    }
                    return
                }
                val body: String
                try {
                    body = response.body()?.string() ?: ""
                } finally {
                    response.close()
                }
                mHandler.post {
                    onEpgRequestResponse(body, date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)
                }
            }
        })
    }

    private fun onEpgRequestFailure(
        date: Date,
        channelNameReal: String,
        finalEpgTagName: String,
        savedEpgKey: String,
        epgQueryNames: ArrayList<String>,
        timeFormat: SimpleDateFormat,
        queryIndex: Int
    ) {
        if (!isCurrentEpgRequest(savedEpgKey)) return
        if (requestNextEpgQueryName(date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)) {
            return
        }
        if (requestDefaultEpgOnFailure(date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)) {
            return
        }
        updateEpgPanelState(false)
    }

    private fun onEpgRequestResponse(
        paramString: String?,
        date: Date,
        channelNameReal: String,
        finalEpgTagName: String,
        savedEpgKey: String,
        epgQueryNames: ArrayList<String>,
        timeFormat: SimpleDateFormat,
        queryIndex: Int
    ) {
        if (!isCurrentEpgRequest(savedEpgKey)) return
        if (paramString.isNullOrBlank()) {
            updateEpgPanelState(false)
            return
        }
        LOG.i("echo-epgTagName:$channelNameReal")
        var arrayList = ArrayList<Epginfo>()
        try {
            if (isXmlEpgResponse(paramString)) {
                arrayList = parseXmlEpg(paramString, finalEpgTagName, date)
            } else if (paramString.contains("epg_data") || paramString.trim().startsWith("{")) {
                arrayList = parseJsonEpg(paramString, date)
            }
        } catch (jSONException: JSONException) {
            jSONException.printStackTrace()
        }
        if (arrayList.isEmpty() && requestNextEpgQueryName(date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, queryIndex)) {
            return
        }
        hsEpg[savedEpgKey] = arrayList
        if (!isCurrentEpgRequest(savedEpgKey)) return
        showEpg(date, arrayList)
        showBottomEpg()
    }

    private fun requestDefaultEpgOnFailure(
        date: Date,
        channelNameReal: String,
        finalEpgTagName: String,
        savedEpgKey: String,
        epgQueryNames: ArrayList<String>?,
        timeFormat: SimpleDateFormat,
        queryIndex: Int
    ): Boolean {
        if (DEFAULT_EPG_ADDRESS == epgStringAddress || epgQueryNames == null || queryIndex >= epgQueryNames.size) {
            return false
        }
        val fallbackUrl = buildEpgUrl(DEFAULT_EPG_ADDRESS, epgQueryNames[0], date, timeFormat)
        LOG.i("echo-epg fallback default address")
        requestEpg(fallbackUrl, date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, epgQueryNames.size)
        return true
    }

    private fun requestNextEpgQueryName(
        date: Date,
        channelNameReal: String,
        finalEpgTagName: String,
        savedEpgKey: String,
        epgQueryNames: ArrayList<String>?,
        timeFormat: SimpleDateFormat,
        queryIndex: Int
    ): Boolean {
        if (!isTemplateEpgAddress(epgStringAddress) || epgQueryNames == null || queryIndex + 1 >= epgQueryNames.size) {
            return false
        }
        val nextIndex = queryIndex + 1
        val nextUrl = buildEpgUrl(epgStringAddress, epgQueryNames[nextIndex], date, timeFormat)
        LOG.i("echo-epg retry query name:" + epgQueryNames[nextIndex])
        requestEpg(nextUrl, date, channelNameReal, finalEpgTagName, savedEpgKey, epgQueryNames, timeFormat, nextIndex)
        return true
    }

    private fun isTemplateEpgAddress(address: String?): Boolean {
        return address != null && (address.contains("{name}") || address.contains("{date}"))
    }

    private fun isCurrentEpgRequest(savedEpgKey: String): Boolean {
        val currentChannel = channel_Name ?: return false
        val adapter = liveEpgDateAdapter ?: return false
        val idx = adapter.getSelectedIndex()
        if (idx < 0 || idx >= adapter.data.size) return false
        val item = adapter.getItem(idx) ?: return false
        val currentEpgKey = (currentChannel.getChannelName() ?: "") + "_" + (item.getDatePresented() ?: "")
        return savedEpgKey == currentEpgKey
    }

    // 显示底部EPG
    private fun isXmlEpgAddress(address: String?): Boolean {
        if (address == null) {
            return false
        }
        var lowerAddress = address.lowercase(Locale.ROOT)
        val queryIndex = lowerAddress.indexOf("?")
        if (queryIndex >= 0) {
            lowerAddress = lowerAddress.substring(0, queryIndex)
        }
        return lowerAddress.endsWith(".xml")
    }

    private fun isXmlEpgResponse(response: String?): Boolean {
        if (response == null) {
            return false
        }
        val trimResponse = response.trim()
        return trimResponse.startsWith("<?xml") || trimResponse.startsWith("<tv") || trimResponse.contains("<programme")
    }

    @Throws(JSONException::class)
    private fun parseJsonEpg(response: String, date: Date): ArrayList<Epginfo> {
        val epgList = ArrayList<Epginfo>()
        val jsonObject = JSONObject(response)
        val channelName = jsonObject.optString("channel_name", jsonObject.optString("channel", ""))
        if (isUnavailableEpgText(channelName)) {
            return epgList
        }
        val epgArray = findJsonEpgArray(jsonObject) ?: return epgList
        for (i in 0 until epgArray.length()) {
            val item = epgArray.optJSONObject(i) ?: continue
            val title = cleanEpgTitle(item.optString("title", item.optString("name", "")))
            if (TextUtils.isEmpty(title) || isUnavailableEpgText(title)) {
                continue
            }
            val startText = item.optString("start", item.optString("start_time", item.optString("starttime", "")))
            val endText = item.optString("end", item.optString("end_time", item.optString("endtime", "")))
            val startDate = parseJsonEpgDate(date, startText)
            var endDate = parseJsonEpgDate(date, endText)
            if (startDate == null || endDate == null) {
                continue
            }
            if (!endDate.after(startDate)) {
                endDate = Date(endDate.time + TimeUnit.DAYS.toMillis(1))
            }
            epgList.add(createXmlEpgInfo(date, title, startDate, endDate, epgList.size))
        }
        return epgList
    }

    private fun findJsonEpgArray(jsonObject: JSONObject): JSONArray? {
        var epgArray = jsonObject.optJSONArray("epg_data")
        if (epgArray != null) return epgArray
        epgArray = jsonObject.optJSONArray("data")
        if (epgArray != null) return epgArray
        epgArray = jsonObject.optJSONArray("list")
        if (epgArray != null) return epgArray
        val dataObject = jsonObject.optJSONObject("data")
        if (dataObject != null) {
            epgArray = dataObject.optJSONArray("epg_data")
            if (epgArray != null) return epgArray
            epgArray = dataObject.optJSONArray("list")
        }
        return epgArray
    }

    private fun parseJsonEpgDate(date: Date, timeText: String?): Date? {
        if (timeText.isNullOrBlank()) {
            return null
        }
        val trimText = timeText.trim()
        val fullPatterns = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")
        for (pattern in fullPatterns) {
            try {
                val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
                return dateFormat.parse(trimText)
            } catch (ignored: ParseException) {
            }
        }
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dayFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
        val dayText = dayFormat.format(date)
        val timePatterns = arrayOf("HH:mm:ss", "HH:mm")
        for (pattern in timePatterns) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd $pattern", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
                return dateFormat.parse("$dayText $trimText")
            } catch (ignored: ParseException) {
            }
        }
        return null
    }

    private fun cleanEpgTitle(title: String?): String {
        if (title == null) {
            return ""
        }
        return title.replace(" --免费使用", "")
            .replace("--免费使用", "")
            .trim()
    }

    private fun isUnavailableEpgText(text: String?): Boolean {
        return text != null && (text.contains("未提供") || text.contains("暂无"))
    }

    private fun normalizeEpgChannelName(channelName: String?): String {
        if (channelName == null) {
            return ""
        }
        val trimName = channelName.trim()
        val compactName = trimName.replace("-", "").replace(" ", "")
        val cctvMatcher = Pattern.compile("(?i)^(CCTV\\d+(?:\\+|K)?)(?:[\\u4e00-\\u9fa5].*|$)").matcher(compactName)
        if (cctvMatcher.matches()) {
            return cctvMatcher.group(1).uppercase(Locale.ROOT)
        }
        if (compactName.uppercase(Locale.ROOT).startsWith("CCTV")) {
            return compactName.uppercase(Locale.ROOT)
        }
        return trimName
    }

    private fun parseXmlEpg(xml: String, channelName: String, date: Date): ArrayList<Epginfo> {
        val epgList = ArrayList<Epginfo>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isIgnoringComments = true
            factory.isCoalescing = true
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (ignored: Exception) {
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val document = builder.parse(InputSource(StringReader(xml)))
            document.documentElement.normalize()

            val targetName = normalizeEpgChannelName(channelName)
            val channelIds = ArrayList<String>()
            val channelNodes = document.getElementsByTagName("channel")
            for (i in 0 until channelNodes.length) {
                val channelNode = channelNodes.item(i)
                if (channelNode.nodeType != Node.ELEMENT_NODE) {
                    continue
                }
                val channelElement = channelNode as Element
                val channelId = channelElement.getAttribute("id")
                if (targetName == normalizeEpgChannelName(channelId)) {
                    channelIds.add(channelId)
                    continue
                }
                val displayNameNodes = channelElement.getElementsByTagName("display-name")
                for (j in 0 until displayNameNodes.length) {
                    val displayName = displayNameNodes.item(j).textContent
                    if (targetName == normalizeEpgChannelName(displayName)) {
                        channelIds.add(channelId)
                        break
                    }
                }
            }

            val dayStart = getDayStart(date)
            val dayEnd = Date(dayStart.time + TimeUnit.DAYS.toMillis(1))
            val programmeNodes = document.getElementsByTagName("programme")
            for (i in 0 until programmeNodes.length) {
                val programmeNode = programmeNodes.item(i)
                if (programmeNode.nodeType != Node.ELEMENT_NODE) {
                    continue
                }
                val programmeElement = programmeNode as Element
                val programmeChannel = programmeElement.getAttribute("channel")
                if (!channelIds.contains(programmeChannel) && targetName != normalizeEpgChannelName(programmeChannel)) {
                    continue
                }

                val startDate = parseXmlTvDate(programmeElement.getAttribute("start"))
                val endDate = parseXmlTvDate(programmeElement.getAttribute("stop"))
                if (startDate == null || endDate == null || !endDate.after(startDate)) {
                    continue
                }
                if (!startDate.before(dayEnd) || !endDate.after(dayStart)) {
                    continue
                }

                var title = ""
                val titleNodes = programmeElement.getElementsByTagName("title")
                if (titleNodes.length > 0) {
                    title = titleNodes.item(0).textContent
                }
                epgList.add(createXmlEpgInfo(date, title, startDate, endDate, epgList.size))
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return epgList
    }

    @Throws(ParseException::class)
    private fun getDayStart(date: Date): Date {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dayFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
        return dayFormat.parse(dayFormat.format(date)) ?: date
    }

    private fun parseXmlTvDate(dateText: String?): Date? {
        if (dateText.isNullOrBlank()) {
            return null
        }
        val trimDate = dateText.trim()
        try {
            return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).parse(trimDate)
        } catch (ignored: ParseException) {
        }
        try {
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
            return dateFormat.parse(trimDate)
        } catch (ignored: ParseException) {
        }
        return null
    }

    private fun createXmlEpgInfo(epgDate: Date, title: String, startDate: Date, endDate: Date, index: Int): Epginfo {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val epgInfo = Epginfo(epgDate, title, epgDate, timeFormat.format(startDate), timeFormat.format(endDate), index)
        epgInfo.startdateTime = startDate
        epgInfo.enddateTime = endDate
        val s = timeFormat.format(startDate)
        val e = timeFormat.format(endDate)
        epgInfo.start = s
        epgInfo.end = e
        epgInfo.originStart = s
        epgInfo.originEnd = e
        epgInfo.datestart = s.replace(":", "").toIntOrNull() ?: 0
        epgInfo.dateend = e.replace(":", "").toIntOrNull() ?: 0
        return epgInfo
    }

    @SuppressLint("SetTextI18n")
    private fun showBottomEpg() {
        if (isSHIYI) {
            return
        }
        val currentChannel = channel_Name ?: return
        val channelName = currentChannel.getChannelName()
        if (channelName != null) {
            tip_chname?.text = channelName
            tv_channelnum?.text = "${currentChannel.getChannelNum()}"
            val tv_current_program_name = findViewById<TextView>(R.id.tv_current_program_name)
            val tv_next_program_name = findViewById<TextView>(R.id.tv_next_program_name)
            if (tv_current_program_name != null && tv_next_program_name != null) {
                setDefaultBottomEpg(tv_current_program_name, tv_next_program_name)
            }
            val selectedDate = liveEpgDateAdapter?.let { adapter ->
                val idx = adapter.getSelectedIndex()
                if (idx >= 0 && idx < adapter.data.size) adapter.getItem(idx)?.getDatePresented() else null
            }
            val savedEpgKey = channelName + "_" + (selectedDate ?: "")

            if (hsEpg.containsKey(savedEpgKey)) {
                val arrayList = hsEpg[savedEpgKey]
                if (arrayList != null && arrayList.isNotEmpty()) {
                    val date = Date()
                    var size = arrayList.size - 1
                    var hasInfo = false
                    while (size >= 0) {
                        val item = arrayList[size]
                        val sDate = item.startdateTime
                        val eDate = item.enddateTime
                        if (sDate != null && eDate != null && date.after(sDate) && date.before(eDate)) {
                            tip_epg1?.text = "${item.start}-${item.end}"
                            tv_current_program_name?.text = item.title
                            if (size != arrayList.size - 1) {
                                val nextItem = arrayList[size + 1]
                                tip_epg2?.text = "${nextItem.start}-${nextItem.end}"
                                tv_next_program_name?.text = nextItem.title
                            } else {
                                tip_epg2?.text = "${item.end}-23:59"
                                tv_next_program_name?.text = "精彩节目-暂无节目预告信息"
                            }
                            hasInfo = true
                            break
                        } else {
                            size--
                        }
                    }
                }
                epgListAdapter?.CanBack(currentLiveChannelItem?.getinclude_back() ?: false)
                epgListAdapter?.setNewData(arrayList)
                updateEpgPanelState(!arrayList.isNullOrEmpty())
            } else {
                updateEpgPanelState(false)
            }

            countDownTimer?.cancel()
            if (tip_epg1?.text != "暂无信息") {
                ll_right_top_loading?.visibility = View.VISIBLE
                updateTopStatusVisibility()
                ll_epg?.visibility = View.VISIBLE
                countDownTimer = object : CountDownTimer(postTimeout.toLong(), 1000) {
                    override fun onTick(j: Long) {}
                    override fun onFinish() {
                        ll_right_top_loading?.visibility = View.GONE
                        ll_right_top_huikan?.visibility = View.GONE
                        ll_epg?.visibility = View.GONE
                        updateTopStatusVisibility()
                    }
                }.start()
            } else {
                ll_right_top_loading?.visibility = View.GONE
                ll_right_top_huikan?.visibility = View.GONE
                ll_epg?.visibility = View.GONE
                updateTopStatusVisibility()
            }

            val tvSource = findViewById<TextView>(R.id.tv_source)
            if (channel_Name == null || (channel_Name?.getSourceNum() ?: 0) <= 0) {
                tvSource?.text = "1/1"
            } else {
                tvSource?.text = "线路${(channel_Name?.getSourceIndex() ?: 0) + 1}/${channel_Name?.getSourceNum() ?: 1}"
            }
            tv_right_top_channel_name?.text = channel_Name?.getChannelName()
            tv_right_top_epg_name?.text = channel_Name?.getChannelName()
        }
    }

    private fun setDefaultBottomEpg(currentProgramName: TextView, nextProgramName: TextView) {
        val timeZone = TimeZone.getTimeZone("GMT+8:00")
        val currentStart = Calendar.getInstance(timeZone)
        currentStart.set(Calendar.MINUTE, 0)
        currentStart.set(Calendar.SECOND, 0)
        currentStart.set(Calendar.MILLISECOND, 0)
        val currentEnd = currentStart.clone() as Calendar
        currentEnd.add(Calendar.MINUTE, 59)
        val nextStart = currentEnd.clone() as Calendar
        nextStart.add(Calendar.MINUTE, 1)
        val nextEnd = nextStart.clone() as Calendar
        nextEnd.add(Calendar.MINUTE, 59)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeFormat.timeZone = timeZone
        tip_epg1?.text = "${timeFormat.format(currentStart.time)}-${timeFormat.format(currentEnd.time)}"
        currentProgramName.text = "精彩节目"
        tip_epg2?.text = "${timeFormat.format(nextStart.time)}-${timeFormat.format(nextEnd.time)}"
        nextProgramName.text = "暂无节目预告信息"
    }

    private fun updateCurrentChannelIcon() {
        val current = channel_Name ?: return
        val channelName = current.getChannelName() ?: return
        val channelNameReal = normalizeEpgChannelName(getFirstPartBeforeSpace(channelName))
        var epgTagName = channelNameReal
        var iconUrl: String? = null
        val logo = logoUrl
        val chLogo = current.getChannelLogo()
        if (!chLogo.isNullOrEmpty()) {
            iconUrl = chLogo
        } else if (logo == null || logo.isEmpty()) {
            val epgInfo = EpgUtil.getEpgInfo(channelNameReal)
            if (epgInfo != null) {
                iconUrl = epgInfo[0]
                if (!epgInfo[1].isEmpty()) {
                    epgTagName = epgInfo[1]
                }
            }
        } else if (logo != "false") {
            iconUrl = logo.replace("{name}", epgTagName)
        }
        updateChannelIcon(channelName, iconUrl)
    }

    @SuppressLint("SetTextI18n")
    private fun updateChannelIcon(channelName: String, logoUrl: String?) {
        val current = channel_Name ?: return
        if (current.getChannelName() == null || current.getChannelName() != channelName) {
            return
        }
        if (StringUtils.isEmpty(logoUrl)) {
            imgLiveIcon?.setImageDrawable(null)
            liveIconNullBg?.visibility = View.VISIBLE
            liveIconNullText?.visibility = View.VISIBLE
            imgLiveIcon?.visibility = View.INVISIBLE
            liveIconNullText?.text = "${current.getChannelNum()}"
        } else {
            imgLiveIcon?.visibility = View.VISIBLE
            imgLiveIcon?.let {
                ImgUtil.load(DefaultConfig.checkReplaceProxy(logoUrl), it, 1, 0, 0, current.getChannelName(), ImageView.ScaleType.CENTER_INSIDE)
            }
            liveIconNullBg?.visibility = View.INVISIBLE
            liveIconNullText?.visibility = View.INVISIBLE
        }
    }

    // 频道列表
    @SuppressLint("NotifyDataSetChanged")
    fun divLoadEpgRight(view: View?) {
        val data = epgListAdapter?.data
        if (data == null || data.isEmpty()) {
            updateEpgPanelState(false)
            return
        }
        mHandler.removeCallbacks(mHideChannelListRun)
        mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
        mChannelGroupView?.visibility = View.GONE
        divEpg?.visibility = View.VISIBLE
        mRightEpgList?.visibility = View.VISIBLE
        divLoadEpgleft?.visibility = View.VISIBLE
        divLoadEpg?.visibility = View.GONE
        liveChannelItemAdapter?.setFocusedChannelIndex(-1)
        epgListAdapter?.notifyDataSetChanged()
        mRightEpgList?.post {
            focusCurrentEpgInMenu()
        }
    }

    // 频道列表
    fun divLoadEpgLeft(view: View?) {
        mHandler.removeCallbacks(mHideChannelListRun)
        mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
        mChannelGroupView?.visibility = View.VISIBLE
        divEpg?.visibility = View.GONE
        divLoadEpgleft?.visibility = View.GONE
        divLoadEpg?.visibility = View.VISIBLE
        focusCurrentChannelInMenu()
    }

    override fun onBackPressed() {
        if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        } else if (tvRightSettingLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        } else if (backcontroller?.visibility == View.VISIBLE) {
            backcontroller?.visibility = View.GONE
        } else if (isBack) {
            isBack = false
            playPreSource()
        } else {
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
            mHandler.removeCallbacks(mUpdateNetSpeedRun)
            exitingLivePlay = true
            super.onBackPressed()
        }
    }

    private val mPlaySelectedChannel = Runnable {
        val channelNumber = selectedChannelNumber
        selectedChannelNumber = 0
        var currentTotal = 0
        var groupIndex = 0
        var channelIndex = -1
        for (group in liveChannelGroupList) {
            val groupChannelCount = group.getLiveChannels()?.size ?: 0
            if (currentTotal + groupChannelCount >= channelNumber) {
                channelIndex = channelNumber - currentTotal - 1 // 转换为0-based索引
                break
            }
            currentTotal += groupChannelCount
            groupIndex++
        }
        tvSelectedChannel?.visibility = View.INVISIBLE
        tvSelectedChannel?.text = ""
        if (channelIndex >= 0) {
            loadChannelGroupDataAndPlay(groupIndex, channelIndex)
        } else {
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, false)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun numericKeyDown(digit: Int) {
        selectedChannelNumber = selectedChannelNumber * 10 + digit
        tvSelectedChannel?.text = selectedChannelNumber.toString()
        ll_right_top_loading?.visibility = View.GONE
        ll_right_top_huikan?.visibility = View.GONE
        tvSelectedChannel?.visibility = View.VISIBLE

        mHandler.removeCallbacks(mPlaySelectedChannel)
        mHandler.postDelayed(mPlaySelectedChannel, 2500)
    }

    private val mmHandler = Handler(Looper.getMainLooper())
    private var mLongPressRunnable: Runnable? = null
    private val LONG_PRESS_DELAY = 800L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isFocusInView(mChannelGroupView)) {
                    focusChannelFromSelectedGroup()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isFocusInView(mLiveChannelView)) {
                    divLoadEpgRight(null)
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && isFocusInView(mRightEpgList)) {
                    divLoadEpgLeft(null)
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !isFocusInView(mLiveChannelView) && !isFocusInView(mRightEpgList)) {
                    focusCurrentGroupInMenu()
                    return true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_HELP) {
                showSettingGroup()
            } else if (!isListOrSettingLayoutVisible()) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playNext()
                        else
                            playPrevious()
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playPrevious()
                        else
                            playNext()
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isBack) {
                            showProgressBars(true)
                        } else {
                            playPreSource()
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isBack) {
                            showProgressBars(true)
                        } else {
                            playNextSource()
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    }
                    else -> {
                        var code = keyCode
                        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                            code -= KeyEvent.KEYCODE_0
                        } else if (code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
                            code -= KeyEvent.KEYCODE_NUMPAD_0
                        } else {
                            return super.dispatchKeyEvent(event)
                        }
                        numericKeyDown(code)
                    }
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (!isListOrSettingLayoutVisible()) {
                if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) && event.repeatCount == 0) {
                    showChannelList()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) && event?.repeatCount == 0) {
            mLongPressRunnable = Runnable {
                showSettingGroup() // 实现长按调出菜单
            }
            mLongPressRunnable?.let { mmHandler.postDelayed(it, LONG_PRESS_DELAY) }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            val r = mLongPressRunnable
            if (r != null) {
                mmHandler.removeCallbacks(r)
                mLongPressRunnable = null
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        exitingLivePlay = false
        mVideoView?.resume()
    }

    override fun onPause() {
        super.onPause()
        if (mVideoView != null && !exitingLivePlay) {
            mVideoView?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, false)
        hideSwitchChannelSnapshot()
        mVideoView?.release()
        mVideoView = null
        mHandler.removeCallbacks(mLoadEpgRun)
        mHandler.removeCallbacks(mUpdateResolutionInfoRun)
        mHandler.removeCallbacks(mHideResolutionInfoRun)
    }

    private fun showChannelList() {
        if (tvRightSettingLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
            return
        }
        if (liveChannelGroupList.isEmpty()) return
        if (tvLeftChannelListLayout?.visibility == View.INVISIBLE) {
            if (currentLiveLookBackIndex > -1) {
                mRightEpgList?.setSelectedPosition(currentLiveLookBackIndex)
                mRightEpgList?.post {
                    mRightEpgList?.smoothScrollToPosition(currentLiveLookBackIndex)
                }
            }
            refreshChannelList(currentChannelGroupIndex)
            mHandler.postDelayed(mFocusCurrentChannelAndShowChannelList, 50)
        } else {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        }
    }

    private var mLastChannelGroupIndex = -1
    private var mLastChannelList: MutableList<LiveChannelItem> = ArrayList()

    private fun refreshChannelList(currentChannelGroupIndex: Int) {
        val newChannels = getLiveChannels(currentChannelGroupIndex)
        // 2. 判断数据是否变化
        if (currentChannelGroupIndex == mLastChannelGroupIndex && isSameData(newChannels, mLastChannelList)) {
            return // 数据未变化，跳过刷新 解决部分直播频道过多时卡顿
        }
        if (currentLiveChannelIndex > -1) {
            mLiveChannelView?.scrollToPosition(currentLiveChannelIndex)
            mLiveChannelView?.setSelection(currentLiveChannelIndex)
        }
        mChannelGroupView?.scrollToPosition(currentChannelGroupIndex)
        mChannelGroupView?.setSelection(currentChannelGroupIndex)
        mLastChannelGroupIndex = currentChannelGroupIndex
        mLastChannelList = ArrayList(newChannels)
        liveChannelItemAdapter?.setNewData(newChannels)
    }

    // 对比两个列表内容是否相同
    private fun isSameData(list1: List<LiveChannelItem>?, list2: List<LiveChannelItem>?): Boolean {
        if (list1 === list2) return true
        if (list1 == null || list2 == null || list1.size != list2.size) return false
        for (i in list1.indices) {
            if (list1[i] != list2[i]) {
                return false
            }
        }
        return true
    }

    private val mFocusCurrentChannelAndShowChannelList: Runnable = object : Runnable {
        override fun run() {
            val groupView = mChannelGroupView
            val channelView = mLiveChannelView
            val layout = tvLeftChannelListLayout ?: return
            if (groupView?.isScrolling == true || channelView?.isScrolling == true ||
                groupView?.isComputingLayout == true || channelView?.isComputingLayout == true
            ) {
                mHandler.postDelayed(this, 100)
            } else {
                layout.visibility = View.VISIBLE
                focusCurrentChannelInMenu()
                val lp = layout.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    val viewObj = ViewObj(layout, lp)
                    val animator = ObjectAnimator.ofObject(viewObj, "marginLeft", IntEvaluator(), -layout.layoutParams.width, 0)
                    animator.duration = 200
                    animator.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            focusCurrentChannelInMenu()
                            mHandler.removeCallbacks(mHideChannelListRun)
                            mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
                        }
                    })
                    animator.start()
                }
            }
        }
    }

    private fun isFocusInView(view: View?): Boolean {
        val focused = currentFocus
        return focused != null && view != null && (focused === view || isChildOf(view, focused))
    }

    private fun isChildOf(parent: View, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === parent) return true
            val p = current.parent
            if (p !is View) return false
            current = p
        }
        return false
    }

    private fun focusRecyclerPosition(recyclerView: TvRecyclerView?, position: Int) {
        if (recyclerView == null || position < 0) return
        recyclerView.scrollToPosition(position)
        recyclerView.setSelection(position)
        requestRecyclerItemFocus(recyclerView, position, 0)
    }

    private fun focusCurrentGroupInMenu() {
        if (currentChannelGroupIndex < 0) return
        mChannelGroupView?.visibility = View.VISIBLE
        divEpg?.visibility = View.GONE
        divLoadEpgleft?.visibility = View.GONE
        val epgData = epgListAdapter?.data
        divLoadEpg?.visibility = if (!epgData.isNullOrEmpty()) View.VISIBLE else View.GONE
        liveChannelGroupAdapter?.setSelectedGroupIndex(currentChannelGroupIndex)
        liveChannelItemAdapter?.setSelectedChannelIndex(currentLiveChannelIndex)
        liveChannelGroupAdapter?.setFocusedGroupIndex(currentChannelGroupIndex)
        liveChannelItemAdapter?.setFocusedChannelIndex(-1)
        epgListAdapter?.setFocusedEpgIndex(-1)
        mLiveChannelView?.clearFocus()
        mRightEpgList?.clearFocus()
        focusRecyclerPosition(mChannelGroupView, currentChannelGroupIndex)
    }

    private fun focusEpgPosition(position: Int) {
        val rightList = mRightEpgList ?: return
        if (position < 0) return
        val layoutManager = rightList.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val offset = Math.max(0, (rightList.height - resources.getDimensionPixelSize(R.dimen.ts_100)) / 2)
            layoutManager.scrollToPositionWithOffset(position, offset)
        } else {
            rightList.scrollToPosition(position)
        }
        rightList.setSelection(position)
        requestRecyclerItemFocus(rightList, position, 0)
    }

    private fun requestRecyclerItemFocus(recyclerView: TvRecyclerView, position: Int, retryCount: Int) {
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else if (retryCount < 3) {
                requestRecyclerItemFocus(recyclerView, position, retryCount + 1)
            }
        }
    }

    private fun focusCurrentChannelInMenu() {
        if (currentChannelGroupIndex < 0 || currentLiveChannelIndex < 0) return
        if (liveChannelGroupAdapter?.getSelectedGroupIndex() != currentChannelGroupIndex) {
            liveChannelGroupAdapter?.setSelectedGroupIndex(currentChannelGroupIndex)
            val channels = getLiveChannels(currentChannelGroupIndex)
            liveChannelItemAdapter?.setNewData(channels)
            mLastChannelGroupIndex = currentChannelGroupIndex
            mLastChannelList = ArrayList(channels)
        }
        liveChannelGroupAdapter?.setFocusedGroupIndex(-1)
        liveChannelItemAdapter?.setSelectedChannelIndex(currentLiveChannelIndex)
        liveChannelItemAdapter?.setFocusedChannelIndex(currentLiveChannelIndex)
        focusRecyclerPosition(mLiveChannelView, currentLiveChannelIndex)
    }

    private fun focusChannelFromSelectedGroup() {
        var groupIndex = liveChannelGroupAdapter?.getSelectedGroupIndex() ?: -1
        if (groupIndex < 0) groupIndex = currentChannelGroupIndex
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size) return
        if (isNeedInputPassword(groupIndex)) {
            showPasswordDialog(groupIndex, -1)
            return
        }
        if (mChannelGroupView?.visibility != View.VISIBLE) return
        val channelIndex = if (groupIndex == currentChannelGroupIndex && currentLiveChannelIndex >= 0) currentLiveChannelIndex else 0
        liveChannelItemAdapter?.setNewData(getLiveChannels(groupIndex))
        liveChannelGroupAdapter?.setSelectedGroupIndex(groupIndex)
        liveChannelGroupAdapter?.setFocusedGroupIndex(-1)
        liveChannelItemAdapter?.setSelectedChannelIndex(if (groupIndex == currentChannelGroupIndex) currentLiveChannelIndex else -1)
        liveChannelItemAdapter?.setFocusedChannelIndex(channelIndex)
        focusRecyclerPosition(mLiveChannelView, channelIndex)
    }

    private fun focusCurrentEpgInMenu() {
        val data = epgListAdapter?.data
        if (mRightEpgList == null || epgListAdapter == null || data.isNullOrEmpty()) return
        syncCurrentEpgSelection(true)
    }

    private val mHideChannelListRun = Runnable {
        val layout = tvLeftChannelListLayout ?: return@Runnable
        val params = layout.layoutParams as? ViewGroup.MarginLayoutParams
        if (layout.visibility == View.VISIBLE && params != null) {
            val viewObj = ViewObj(layout, params)
            val animator = ObjectAnimator.ofObject(viewObj, "marginLeft", IntEvaluator(), 0, -layout.layoutParams.width)
            animator.duration = 200
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    layout.visibility = View.INVISIBLE
                }
            })
            animator.start()
        }
    }

    private fun showChannelInfo() {
        val currentItem = currentLiveChannelItem ?: return
        tvChannelInfo?.text = String.format(
            Locale.getDefault(), "%d %s %s(%d/%d)", currentItem.getChannelNum(),
            currentItem.getChannelName(), currentItem.getSourceName(),
            currentItem.getSourceIndex() + 1, currentItem.getSourceNum()
        )

        val lParams = FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (tvRightSettingLayout?.visibility == View.VISIBLE) {
            lParams.gravity = Gravity.LEFT
            lParams.leftMargin = 60
            lParams.topMargin = 30
        } else {
            lParams.gravity = Gravity.RIGHT
            lParams.rightMargin = 60
            lParams.topMargin = 30
        }
        tvChannelInfo?.layoutParams = lParams

        tvChannelInfo?.visibility = View.VISIBLE
        mHandler.removeCallbacks(mHideChannelInfoRun)
        mHandler.postDelayed(mHideChannelInfoRun, 3000)
    }

    private val mHideChannelInfoRun = Runnable {
        tvChannelInfo?.visibility = View.INVISIBLE
    }

    private var catchup: JsonObject? = null
    private var logoUrl: String? = null

    private fun initLiveObj() {
        catchup = null
        logoUrl = null
        val position = ApiConfig.getLiveGroupIndex()
        val live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, JsonArray())
        if (live_groups == null || live_groups.size() == 0 || position < 0 || position >= live_groups.size()) {
            return
        }
        val livesOBJ = live_groups.get(position).asJsonObject
        val type = if (livesOBJ.has("type")) livesOBJ.get("type").asString else "0"

        if (livesOBJ.has("catchup") && livesOBJ.get("catchup").isJsonObject) {
            catchup = livesOBJ.getAsJsonObject("catchup")
            LOG.i("echo-catchup :$catchup")
        }
        if (livesOBJ.has("logo")) {
            logoUrl = livesOBJ.get("logo").asString
        }
        if (type == "3") {
            var py_jar = ""
            if (livesOBJ.has("jar")) {
                py_jar = if (livesOBJ.has("jar")) livesOBJ.get("jar").asString else ""
            } else if (livesOBJ.has("api")) {
                py_jar = if (livesOBJ.has("api")) livesOBJ.get("api").asString else ""
                var ext = ""
                if (livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject || livesOBJ.get("ext").isJsonArray)) {
                    ext = livesOBJ.get("ext").toString()
                } else {
                    ext = DefaultConfig.safeJsonString(livesOBJ, "ext", "")
                }
                LOG.i("echo-ext:$ext")
                if (ext.isNotEmpty()) py_jar = "$py_jar?extend=$ext"
            }
            ApiConfig.get().setLiveJar(py_jar)
        }
    }

    private fun liveWebHeader(): HashMap<String, String>? {
        return Hawk.get(HawkConfig.LIVE_WEB_HEADER)
    }

    private fun liveChannelHeader(): HashMap<String, String>? {
        val currentItem = currentLiveChannelItem ?: return liveWebHeader()
        val header = HashMap<String, String>()
        val liveHeader = liveWebHeader()
        if (liveHeader != null) header.putAll(liveHeader)
        val chHeaders = currentItem.getHeaders()
        if (chHeaders != null) {
            header.putAll(chHeaders)
        }
        val chFormat = currentItem.getChannelFormat()
        if (!chFormat.isNullOrEmpty()) {
            header[ExoMediaSourceHelper.HEADER_FORMAT] = chFormat
        }
        return if (header.isEmpty()) null else header
    }

    private fun currentChannelHasCatchup(): Boolean {
        return currentLiveChannelItem != null && hasCatchupSource(currentLiveChannelItem?.getChannelCatchup())
    }

    private fun currentCatchup(): JsonObject? {
        if (currentChannelHasCatchup()) return currentLiveChannelItem?.getChannelCatchup()
        return catchup
    }

    private fun getCatchupValue(catchupObj: JsonObject?, key: String): String {
        if (catchupObj == null || !catchupObj.has(key) || catchupObj.get(key).isJsonNull) return ""
        return try {
            catchupObj.get(key).asString
        } catch (ignored: Throwable) {
            ""
        }
    }

    private fun hasCatchupSource(catchupObj: JsonObject?): Boolean {
        return !TextUtils.isEmpty(getCatchupValue(catchupObj, "source"))
    }

    private fun canCurrentChannelCatchup(): Boolean {
        val current = currentLiveChannelItem ?: return false
        val url = current.getUrl()
        val catchupObj = currentCatchup()
        if (hasCatchupSource(catchupObj)) {
            val regex = getCatchupValue(catchupObj, "regex")
            if (TextUtils.isEmpty(regex)) return true
            return try {
                url.contains(regex) || Pattern.compile(regex).matcher(url).find()
            } catch (ignored: Throwable) {
                false
            }
        }
        return url.contains("/PLTV/")
    }

    private fun buildCatchupUrl(url: String?, epg: Epginfo?): String {
        val sDate = epg?.startdateTime
        val eDate = epg?.enddateTime
        if (TextUtils.isEmpty(url) || epg == null || sDate == null || eDate == null) return ""
        val catchupObj = currentCatchup()
        if (hasCatchupSource(catchupObj)) {
            return formatCatchupUrl(url!!, catchupObj, epg)
        }
        if (!url!!.contains("/PLTV/")) return ""
        val source = "?playseek=" + formatCatchupTime(sDate, "yyyyMMddHHmmss") +
                "-" + formatCatchupTime(eDate, "yyyyMMddHHmmss")
        return appendCatchupUrl(url, "/PLTV/,/TVOD/", source)
    }

    private fun formatCatchupUrl(url: String, catchupObj: JsonObject?, epg: Epginfo): String {
        val source = formatCatchupSource(getCatchupValue(catchupObj, "source"), epg)
        if ("default".equals(getCatchupValue(catchupObj, "type"), ignoreCase = true)) return source
        return appendCatchupUrl(url, getCatchupValue(catchupObj, "replace"), source)
    }

    private fun appendCatchupUrl(url: String, replace: String, source: String): String {
        var replayUrl = url
        var src = source
        val parts = replace.split(",", limit = 2)
        if (parts.size == 2 && !TextUtils.isEmpty(parts[0])) {
            try {
                replayUrl = replayUrl.replace(parts[0].toRegex(), parts[1])
            } catch (ignored: Throwable) {
            }
        }
        val queryIndex = replayUrl.indexOf('?')
        if (queryIndex in 0 until (replayUrl.length - 1)) src = src.replace("?", "&")
        return replayUrl + src
    }

    private fun formatCatchupSource(source: String, epg: Epginfo): String {
        val matcher = CATCHUP_TOKEN_PATTERN.matcher(source)
        val result = StringBuffer()
        while (matcher.find()) {
            val token = matcher.group(1) ?: ""
            matcher.appendReplacement(result, Matcher.quoteReplacement(formatCatchupToken(token, epg)))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    private fun formatCatchupToken(token: String, epg: Epginfo): String {
        val matcher = CATCHUP_TAG_PATTERN.matcher(token)
        if (!matcher.find()) return ""
        val tag = matcher.group(1) ?: ""
        val sDate = epg.startdateTime
        val eDate = epg.enddateTime
        if (tag.startsWith("utcend:") && eDate != null) return (eDate.time / 1000).toString()
        if (tag.startsWith("utc:") && sDate != null) return (sDate.time / 1000).toString()
        val bracketIndex = tag.indexOf(')')
        if (tag.startsWith("(b") && bracketIndex >= 0 && sDate != null) return formatCatchupTime(sDate, tag.substring(bracketIndex + 1))
        if (tag.startsWith("(e") && bracketIndex >= 0 && eDate != null) return formatCatchupTime(eDate, tag.substring(bracketIndex + 1))
        return ""
    }

    private fun formatCatchupTime(time: Date, pattern: String): String {
        if ("timestamp" == pattern) return (time.time / 1000).toString()
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).format(time)
        } catch (ignored: IllegalArgumentException) {
            ""
        }
    }

    private fun getCatchupDurationSeconds(epg: Epginfo?): Int {
        val sDate = epg?.startdateTime
        val eDate = epg?.enddateTime
        if (epg == null || sDate == null || eDate == null) return 0
        val duration = Math.max(0L, eDate.time - sDate.time) / 1000
        return if (duration > Int.MAX_VALUE) Int.MAX_VALUE else duration.toInt()
    }

    private fun showSwitchChannelSnapshot() {
        if (switchChannelSnapshotImage != null && mVideoView != null) {
            var bitmap: Bitmap? = null
            try {
                bitmap = mVideoView?.doScreenShot()
            } catch (ignored: Throwable) {
            }
            if (bitmap != null) {
                switchChannelSnapshotImage?.setImageBitmap(bitmap)
                switchChannelSnapshotImage?.visibility = View.VISIBLE
            } else {
                switchChannelSnapshotImage?.setImageBitmap(null)
                switchChannelSnapshotImage?.visibility = View.GONE
            }
        }
        switchChannelSnapshotOverlay?.visibility = View.VISIBLE
    }

    private fun hideSwitchChannelSnapshot() {
        switchChannelSnapshotOverlay?.visibility = View.GONE
        switchChannelSnapshotImage?.setImageBitmap(null)
        switchChannelSnapshotImage?.visibility = View.GONE
    }

    private fun playChannel(channelGroupIndex: Int, liveChannelIndex: Int, changeSource: Boolean): Boolean {
        val curItem = currentLiveChannelItem
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource) ||
            (changeSource && curItem != null && curItem.getSourceNum() == 1)
        ) {
            return true
        }
        val groupChannels = getLiveChannels(channelGroupIndex)
        if (groupChannels.isEmpty() || liveChannelIndex < 0 || liveChannelIndex >= groupChannels.size) {
            return false
        }
        val showPreviousFrame = currentLiveChannelItem != null && mVideoView != null && mVideoView?.isPlaying() == true
        val previousLivePlayerType = livePlayerManager.getLivePlayerType()
        allowLiveSwitchPlayer = true
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex
            currentLiveChannelIndex = liveChannelIndex
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex)[currentLiveChannelIndex]
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem?.getChannelName())
        }

        channel_Name = currentLiveChannelItem
        currentLiveLookBackIndex = -1
        epgListAdapter?.setSelectedEpgIndex(-1)
        isSHIYI = false
        isBack = false
        if (canCurrentChannelCatchup()) {
            currentLiveChannelItem?.setinclude_back(true)
        } else {
            currentLiveChannelItem?.setinclude_back(false)
        }
        updateCurrentChannelIcon()
        showBottomEpg()
        backcontroller?.visibility = View.GONE
        ll_right_top_huikan?.visibility = View.GONE
        val videoView = mVideoView
        if (videoView != null) {
            if (liveChannelHeader() != null) LOG.i("echo-${liveChannelHeader().toString()}")
            val reusePlayer = canReusePlayer(previousLivePlayerType)
            val keepExoFrame = reusePlayer && previousLivePlayerType == 2
            if (showPreviousFrame && !keepExoFrame) {
                showSwitchChannelSnapshot()
            } else {
                hideSwitchChannelSnapshot()
            }
            val liveUrl = currentLiveChannelItem?.getUrl() ?: ""
            if (reusePlayer) {
                videoView.setUrl(liveUrl, liveChannelHeader())
                videoView.replay(true)
            } else {
                videoView.release()
                videoView.setUrl(liveUrl, liveChannelHeader())
                videoView.start()
            }
            showResolutionAfterChannelSwitch()
        }
        loadEpgAfterChannelStarted()
        return true
    }

    private fun canReusePlayer(previousLivePlayerType: Int): Boolean {
        val currentLivePlayerType = livePlayerManager.getLivePlayerType()
        return mVideoView != null &&
                mVideoView?.getCurrentPlayState() != VideoView.STATE_IDLE &&
                previousLivePlayerType == currentLivePlayerType
    }

    private fun loadEpgAfterChannelStarted() {
        mHandler.removeCallbacks(mLoadEpgRun)
        if (!hasEpgAddress()) {
            updateEpgPanelState(false)
            return
        }
        if (hasCurrentEpgCache()) {
            firstLiveEpgLoad = false
            return
        }
        if (firstLiveEpgLoad) {
            firstLiveEpgLoad = false
            mHandler.postDelayed(mLoadEpgRun, EPG_LOAD_DELAY)
        } else {
            getEpg(Date())
        }
    }

    private fun hasCurrentEpgCache(): Boolean {
        val currentChannel = channel_Name ?: return false
        val adapter = liveEpgDateAdapter ?: return false
        val idx = adapter.getSelectedIndex()
        if (idx < 0 || idx >= adapter.data.size) return false
        val item = adapter.getItem(idx) ?: return false
        val currentEpgKey = (currentChannel.getChannelName() ?: "") + "_" + (item.getDatePresented() ?: "")
        return hsEpg.containsKey(currentEpgKey)
    }

    private fun playNext() {
        if (!isCurrentLiveChannelValid()) return
        val groupChannelIndex = getNextChannel(1)
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false)
    }

    private fun playPrevious() {
        if (!isCurrentLiveChannelValid()) return
        val groupChannelIndex = getNextChannel(-1)
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false)
    }

    fun playPreSource() {
        if (!isCurrentLiveChannelValid()) return
        currentLiveChannelItem?.preSource()
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
    }

    fun playNextSource() {
        if (!isCurrentLiveChannelValid()) return
        currentLiveChannelItem?.nextSource()
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
    }

    // 显示设置列表
    private fun showSettingGroup() {
        if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        }
        if (tvRightSettingLayout?.visibility == View.INVISIBLE) {
            // 重新载入默认状态
            ApiConfig.get().refreshLiveApiHistoryItems()
            loadCurrentSourceList()
            liveSettingGroupAdapter?.setNewData(getVisibleLiveSettingGroupList())
            liveSettingGroupAdapter?.setSelectedGroupIndex(-1)
            val settingGroupIndex = getDefaultSettingGroupIndex()
            selectSettingGroup(settingGroupIndex, false)
            val settingGroupPosition = liveSettingGroupAdapter?.findPositionByGroupIndex(settingGroupIndex) ?: 0
            mSettingGroupView?.scrollToPosition(if (settingGroupPosition < 0) 0 else settingGroupPosition)
            var settingItemIndex = currentLiveChannelItem?.getSourceIndex() ?: 0
            val itemAdapter = liveSettingItemAdapter
            if (itemAdapter != null && (itemAdapter.data.isEmpty() || settingItemIndex < 0 || settingItemIndex >= itemAdapter.data.size)) {
                settingItemIndex = 0
            }
            mSettingItemView?.scrollToPosition(settingItemIndex)
            mHandler.postDelayed(mFocusAndShowSettingGroup, 50)
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        }
    }

    private val mFocusAndShowSettingGroup: Runnable = object : Runnable {
        override fun run() {
            val groupView = mSettingGroupView
            val itemView = mSettingItemView
            val layout = tvRightSettingLayout ?: return
            if (groupView?.isScrolling == true || itemView?.isScrolling == true ||
                groupView?.isComputingLayout == true || itemView?.isComputingLayout == true
            ) {
                mHandler.postDelayed(this, 100)
            } else {
                val settingGroupIndex = getDefaultSettingGroupIndex()
                val settingGroupPosition = liveSettingGroupAdapter?.findPositionByGroupIndex(settingGroupIndex) ?: 0
                val holder = groupView?.findViewHolderForAdapterPosition(if (settingGroupPosition < 0) 0 else settingGroupPosition)
                holder?.itemView?.requestFocus()
                layout.visibility = View.VISIBLE
                updateTopStatusVisibility()
                val params = layout.layoutParams as? ViewGroup.MarginLayoutParams
                if (layout.visibility == View.VISIBLE && params != null) {
                    val viewObj = ViewObj(layout, params)
                    val animator = ObjectAnimator.ofObject(viewObj, "marginRight", IntEvaluator(), -layout.layoutParams.width, livePanelEdgeMargin())
                    animator.duration = 200
                    animator.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
                        }
                    })
                    animator.start()
                }
            }
        }
    }

    private val mHideSettingLayoutRun = Runnable {
        val layout = tvRightSettingLayout ?: return@Runnable
        val params = layout.layoutParams as? ViewGroup.MarginLayoutParams
        if (layout.visibility == View.VISIBLE && params != null) {
            val viewObj = ViewObj(layout, params)
            val animator = ObjectAnimator.ofObject(viewObj, "marginRight", IntEvaluator(), params.rightMargin, -layout.layoutParams.width)
            animator.duration = 200
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    layout.visibility = View.INVISIBLE
                    updateTopStatusVisibility()
                    liveSettingGroupAdapter?.setSelectedGroupIndex(-1)
                }
            })
            animator.start()
        }
    }

    private fun livePanelEdgeMargin(): Int {
        return 0
    }

    // laodao 7天Epg数据绑定和展示
    private fun initEpgListView() {
        val rightEpgList = mRightEpgList ?: return
        rightEpgList.setHasFixedSize(true)
        rightEpgList.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)
        val adapter = LiveEpgAdapter()
        epgListAdapter = adapter
        rightEpgList.adapter = adapter

        rightEpgList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
            }
        })

        // 电视
        rightEpgList.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                epgListAdapter?.setFocusedEpgIndex(-1)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
                epgListAdapter?.setFocusedEpgIndex(position)
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                handleEpgItemClick(position)
            }
        })

        // 手机/模拟器
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, _, position ->
            handleEpgItemClick(position)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun handleEpgItemClick(position: Int) {
        if (position == currentLiveLookBackIndex) return
        val dateAdapter = liveEpgDateAdapter
        val date = if (dateAdapter == null || dateAdapter.getSelectedIndex() < 0) Date() else dateAdapter.data[dateAdapter.getSelectedIndex()].getDateParamVal() ?: Date()
        val selectedData = epgListAdapter?.getItem(position) ?: return
        val sDate = selectedData.startdateTime
        val eDate = selectedData.enddateTime
        if (sDate == null || eDate == null) return
        val now = Date()
        if (now.compareTo(sDate) < 0) {
            return
        }
        if (now.after(eDate) && !canCurrentChannelCatchup()) return
        currentLiveLookBackIndex = position
        epgListAdapter?.setSelectedEpgIndex(position)
        if (now.compareTo(sDate) >= 0 && now.compareTo(eDate) <= 0) {
            mVideoView?.release()
            isSHIYI = false
            mVideoView?.setUrl(currentLiveChannelItem?.getUrl() ?: "", liveChannelHeader())
            mVideoView?.start()
            epgListAdapter?.setShiyiSelection(-1, false, timeFormat.format(date))
            epgListAdapter?.notifyDataSetChanged()
            showProgressBars(false)
            return
        }
        var shiyiUrl = currentLiveChannelItem?.getUrl() ?: ""
        if (now.compareTo(sDate) < 0) {
        } else if (canCurrentChannelCatchup()) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.postDelayed(mHideChannelListRun, 100)
            mVideoView?.release()
            isSHIYI = true
            shiyiUrl = buildCatchupUrl(shiyiUrl, selectedData)
            if (TextUtils.isEmpty(shiyiUrl)) return
            LOG.i("echo-回看地址playUrl :$shiyiUrl")
            playUrl = shiyiUrl

            if (liveChannelHeader() != null) LOG.i("echo-liveWebHeader :" + liveChannelHeader().toString())
            mVideoView?.setUrl(playUrl, liveChannelHeader())
            mVideoView?.start()
            epgListAdapter?.setShiyiSelection(position, true, timeFormat.format(date))
            epgListAdapter?.notifyDataSetChanged()
            mRightEpgList?.setSelectedPosition(position)
            mRightEpgList?.post {
                mRightEpgList?.smoothScrollToPosition(position)
            }
            shiyi_time_c = getCatchupDurationSeconds(selectedData)
            val lp = iv_play?.layoutParams
            if (lp != null) {
                lp.width = videoHeight / 7
                lp.height = videoHeight / 7
            }
            sBar = findViewById(R.id.pb_progressbar)
            sBar?.max = safeTimeMs((shiyi_time_c.toLong()) * 1000)
            val currentPos = safeTimeMs(mVideoView?.getCurrentPosition() ?: 0)
            sBar?.progress = currentPos
            tv_currentpos?.text = durationToString(currentPos)
            tv_duration?.text = durationToString(safeTimeMs((shiyi_time_c.toLong()) * 1000))
            showProgressBars(true)
            isBack = true
        }
    }

    // laoda 生成7天回放日期列表数据
    private fun initDayList() {
        liveDayList.clear()
        val daylist = LiveDayListGroup()
        val newday = Date(nowday.time)
        val dayStr = formatDate1.format(newday)
        LOG.i("echo-date$dayStr")
        daylist.setGroupIndex(0)
        daylist.setGroupName(dayStr)
        liveDayList.add(daylist)
    }

    // kens 7天回放数据绑定和展示
    private fun initEpgDateView() {
        val dateGridView = mEpgDateGridView ?: return
        dateGridView.setHasFixedSize(true)
        dateGridView.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)
        val adapter = LiveEpgDateAdapter()
        liveEpgDateAdapter = adapter
        val calendar = Calendar.getInstance()
        calendar.time = Date()
        @SuppressLint("SimpleDateFormat") val datePresentFormat = SimpleDateFormat("MM-dd")
        calendar.add(Calendar.DAY_OF_MONTH, 0)
        for (i in 0 until 1) {
            val dateIns = calendar.time
            val epgDate = LiveEpgDate()
            epgDate.setIndex(i)
            epgDate.setDatePresented(datePresentFormat.format(dateIns))
            epgDate.setDateParamVal(dateIns)
            adapter.addData(epgDate)
        }
        dateGridView.adapter = adapter
        dateGridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
            }
        })
        adapter.setSelectedIndex(0)
        dateGridView.visibility = View.GONE
    }

    private fun initVideoView() {
        val controller = LiveController(this)
        controller.setListener(object : LiveController.LiveControlListener {
            override fun singleTap(): Boolean {
                showChannelList()
                return true
            }

            override fun longPress() {
                if (isBack) { // 手机换源和显示时移控制栏
                    showProgressBars(true)
                } else {
                    showSettingGroup()
                }
            }

            override fun playStateChanged(playState: Int) {
                mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
                when (playState) {
                    VideoView.STATE_IDLE, VideoView.STATE_PAUSED -> {
                    }
                    VideoView.STATE_PREPARED, VideoView.STATE_BUFFERED, VideoView.STATE_PLAYING -> {
                        hideSwitchChannelSnapshot()
                        if (resolutionInfoPending) {
                            resolutionInfoRetryCount = 0
                            mHandler.removeCallbacks(mUpdateResolutionInfoRun)
                            mHandler.post(mUpdateResolutionInfoRun)
                        }
                        currentLiveChangeSourceTimes = 0
                        allowLiveSwitchPlayer = true
                    }
                    VideoView.STATE_ERROR, VideoView.STATE_PLAYBACK_COMPLETED -> {
                        hideSwitchChannelSnapshot()
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 3500)
                    }
                    VideoView.STATE_PREPARING, VideoView.STATE_BUFFERING -> {
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000L)
                    }
                    else -> {
                        LOG.i("echo-Unexpected live_play state: $playState")
                    }
                }
            }

            override fun changeSource(direction: Int) {
                if (direction > 0) {
                    if (isBack) {
                        showProgressBars(true)
                    } else {
                        playNextSource()
                    }
                } else {
                    playPreSource()
                }
            }
        })
        controller.setCanChangePosition(false)
        controller.setEnableInNormal(true)
        controller.setGestureEnabled(true)
        controller.setDoubleTapTogglePlayEnabled(false)
        mVideoView?.setVideoController(controller)
        mVideoView?.setProgressManager(null)
    }

    private fun switchLivePlayerAndReplay(): Boolean {
        val videoView = mVideoView
        if (!allowLiveSwitchPlayer || currentLiveChannelItem == null || videoView == null) {
            return false
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
        videoView.release()
        if (!livePlayerManager.switchLivePlayer(videoView)) {
            allowLiveSwitchPlayer = false
            return false
        }
        allowLiveSwitchPlayer = false
        val retryUrl = if (isSHIYI && !TextUtils.isEmpty(playUrl)) playUrl!! else currentLiveChannelItem?.getUrl() ?: ""
        videoView.setUrl(retryUrl, liveChannelHeader())
        videoView.start()
        return true
    }

    private val mConnectTimeoutChangeSourceRun: Runnable = Runnable {
        if (switchLivePlayerAndReplay()) {
            return@Runnable
        }
        currentLiveChangeSourceTimes++
        val curItem = currentLiveChannelItem
        if (curItem != null && curItem.getSourceNum() == currentLiveChangeSourceTimes) {
            currentLiveChangeSourceTimes = 0
            val groupChannelIndex = getNextChannel(if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) -1 else 1)
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false)
        } else {
            playNextSource()
        }
    }

    private fun initChannelGroupView() {
        val groupView = mChannelGroupView ?: return
        groupView.setHasFixedSize(true)
        groupView.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)

        val adapter = LiveChannelGroupAdapter()
        liveChannelGroupAdapter = adapter
        groupView.adapter = adapter
        groupView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
            }
        })

        // 电视
        groupView.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                selectChannelGroup(position, true, -1)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (isNeedInputPassword(position)) {
                    showPasswordDialog(position, -1)
                }
            }
        })

        // 手机/模拟器
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            selectChannelGroup(position, false, -1)
        }
    }

    private fun selectChannelGroup(groupIndex: Int, focus: Boolean, liveChannelIndex: Int) {
        if (focus && tvLeftChannelListLayout?.visibility != View.VISIBLE) return
        mLastChannelGroupIndex = groupIndex
        if (focus) {
            liveChannelGroupAdapter?.setFocusedGroupIndex(groupIndex)
            clearFocusedChannelInMenu()
        }
        if ((groupIndex > -1 && groupIndex != (liveChannelGroupAdapter?.getSelectedGroupIndex() ?: -1)) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter?.setSelectedGroupIndex(groupIndex)
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex)
                return
            }
            if (focus && liveChannelIndex < 0) {
                loadChannelGroupData(groupIndex)
            } else {
                loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex)
            }
        }
        if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
        }
    }

    private fun clearFocusedChannelInMenu() {
        if (tvLeftChannelListLayout?.visibility != View.VISIBLE) return
        val liveView = mLiveChannelView ?: return
        if (liveView.isComputingLayout || liveView.isScrolling) {
            liveView.postDelayed({ clearFocusedChannelInMenu() }, 50)
            return
        }
        liveChannelItemAdapter?.setFocusedChannelIndex(-1)
    }

    private fun initLiveChannelView() {
        val channelView = mLiveChannelView ?: return
        channelView.setHasFixedSize(true)
        channelView.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)

        val adapter = LiveChannelItemAdapter()
        liveChannelItemAdapter = adapter
        channelView.adapter = adapter
        channelView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
            }
        })

        // 电视
        channelView.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (position < 0) return
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
                liveChannelGroupAdapter?.setFocusedGroupIndex(-1)
                liveChannelItemAdapter?.setFocusedChannelIndex(position)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                clickLiveChannel(position)
            }
        })

        // 手机/模拟器
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            liveChannelItemAdapter?.setSelectedChannelIndex(position)
            clickLiveChannel(position)
        }
    }

    private fun clickLiveChannel(position: Int) {
        if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
        }
        playChannel(liveChannelGroupAdapter?.getSelectedGroupIndex() ?: 0, position, false)
    }

    private fun initSettingGroupView() {
        val groupView = mSettingGroupView ?: return
        groupView.setHasFixedSize(true)
        groupView.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)

        val adapter = LiveSettingGroupAdapter()
        liveSettingGroupAdapter = adapter
        groupView.adapter = adapter
        groupView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
            }
        })

        // 电视
        groupView.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
                selectVisibleSettingGroup(position, true)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {}
        })

        // 手机/模拟器
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            selectVisibleSettingGroup(position, false)
        }
    }

    private fun selectVisibleSettingGroup(position: Int, focus: Boolean) {
        val adapter = liveSettingGroupAdapter ?: return
        if (position < 0 || position >= adapter.data.size) return
        val group = adapter.data[position]
        selectSettingGroup(group.getGroupIndex(), focus)
    }

    private fun selectSettingGroup(position: Int, focus: Boolean) {
        val groupAdapter = liveSettingGroupAdapter ?: return
        val itemAdapter = liveSettingItemAdapter ?: return
        if (focus) {
            groupAdapter.setFocusedGroupIndex(position)
            itemAdapter.setFocusedItemIndex(-1)
        }
        if (position == groupAdapter.getSelectedGroupIndex() || position < 0 || position >= liveSettingGroupList.size) {
            return
        }

        groupAdapter.setSelectedGroupIndex(position)
        itemAdapter.setNewData(liveSettingGroupList[position].getLiveSettingItems())

        when (position) {
            0 -> {
                val current = currentLiveChannelItem
                if (current != null && current.getSourceIndex() >= 0 && current.getSourceIndex() < itemAdapter.data.size) {
                    itemAdapter.selectItem(current.getSourceIndex(), true, false)
                }
            }
            1 -> itemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true)
            2 -> itemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true)
            6 -> itemAdapter.selectItem(getCurrentLiveApiHistoryIndex(), true, true)
        }
        var scrollToPosition = itemAdapter.getSelectedItemIndex()
        if (scrollToPosition < 0) scrollToPosition = 0
        mSettingItemView?.scrollToPosition(scrollToPosition)
        mHandler.removeCallbacks(mHideSettingLayoutRun)
        mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
    }

    private fun initSettingItemView() {
        val itemView = mSettingItemView ?: return
        itemView.setHasFixedSize(true)
        itemView.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)

        val adapter = LiveSettingItemAdapter()
        liveSettingItemAdapter = adapter
        itemView.adapter = adapter
        itemView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
            }
        })

        // 电视
        itemView.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (position < 0) return
                liveSettingGroupAdapter?.setFocusedGroupIndex(-1)
                liveSettingItemAdapter?.setFocusedItemIndex(position)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                clickSettingItem(position)
            }
        })

        // 手机/模拟器
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            clickSettingItem(position)
        }
    }

    private fun clickSettingItem(position: Int) {
        val settingGroupIndex = liveSettingGroupAdapter?.getSelectedGroupIndex() ?: -1
        if (settingGroupIndex in 0..2 && !isCurrentLiveChannelValid()) {
            return
        }
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter?.getSelectedItemIndex()) return
            liveSettingItemAdapter?.selectItem(position, true, true)
        }
        when (settingGroupIndex) {
            0 -> { // 线路切换
                val current = currentLiveChannelItem
                if (current != null && (position < 0 || position >= current.getSourceNum())) return
                current?.setSourceIndex(position)
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
            }
            1 -> { // 画面比例
                mVideoView?.let { livePlayerManager.changeLivePlayerScale(it, position) }
            }
            2 -> { // 播放解码
                mVideoView?.release()
                mVideoView?.let { livePlayerManager.changeLivePlayerType(it, position) }
                mVideoView?.setUrl(currentLiveChannelItem?.getUrl() ?: "", liveChannelHeader())
                mVideoView?.start()
            }
            3 -> { // 超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position)
            }
            4 -> { // 偏好设置
                var select = false
                when (position) {
                    0 -> {
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select)
                        showTime()
                    }
                    1 -> {
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select)
                        showNetSpeed()
                    }
                    2 -> {
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select)
                    }
                    3 -> {
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select)
                    }
                }
                liveSettingItemAdapter?.selectItem(position, select, false)
            }
            5 -> { // 多源切换
                if (position == ApiConfig.getLiveGroupIndex()) return
                val currentChannelName = getPreferredLiveRefreshChannelName()
                val currentSourceIndex = getPreferredLiveRefreshSourceIndex()
                val live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, JsonArray())
                if (live_groups == null || position >= live_groups.size()) return
                liveConfigRequestId++
                val livesOBJ = live_groups.get(position).asJsonObject
                liveSettingItemAdapter?.selectItem(position, true, true)
                ApiConfig.setLiveGroupIndex(position)
                ApiConfig.get().loadLiveApi(livesOBJ)
                if (ApiConfig.get().getChannelGroupList().isEmpty()) {
                    mVideoView?.release()
                    setEmptyLiveChannelList(false)
                    return
                }
                refreshLiveChannelListAndPlay(currentChannelName, currentSourceIndex)
            }
            6 -> { // 配置切换
                val history: ArrayList<String> = Hawk.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
                if (history.isEmpty() || position < 0 || position >= history.size) return
                val value = history[position]
                val oldLiveApi = Hawk.get(HawkConfig.LIVE_API_URL, "")
                val configChannelName = getPreferredLiveRefreshChannelName()
                val configSourceIndex = getPreferredLiveRefreshSourceIndex()
                liveSettingItemAdapter?.selectItem(position, true, true)
                if (value == oldLiveApi) return
                val requestId = ++liveConfigRequestId
                Hawk.put(HawkConfig.LIVE_API_URL, value)
                HistoryHelper.setLiveApiHistory(value)
                ApiConfig.get().refreshLiveApiHistoryItems()
                ApiConfig.get().loadLiveConfig(false, object : ApiConfig.LoadConfigCallback {
                    override fun success() {
                        mHandler.post {
                            if (requestId != liveConfigRequestId || isFinishing) return@post
                            refreshLiveChannelListAndPlay(configChannelName, configSourceIndex)
                        }
                    }

                    override fun error(msg: String) {
                        mHandler.post {
                            if (requestId != liveConfigRequestId || isFinishing) return@post
                            mVideoView?.release()
                            ApiConfig.get().refreshLiveApiHistoryItems()
                            setEmptyLiveChannelList(false)
                            Toast.makeText(this@LivePlayActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun notice(msg: String) {
                        mHandler.post {
                            if (requestId != liveConfigRequestId || isFinishing) return@post
                            Toast.makeText(this@LivePlayActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun)
        mHandler.postDelayed(mHideSettingLayoutRun, postTimeout.toLong())
    }

    private fun getPreferredLiveRefreshChannelName(): String {
        val current = currentLiveChannelItem
        if (current != null) return current.getChannelName() ?: ""
        return Hawk.get(HawkConfig.LIVE_CHANNEL, "")
    }

    private fun getPreferredLiveRefreshSourceIndex(): Int {
        val current = currentLiveChannelItem
        if (current != null) return current.getSourceIndex()
        return -1
    }

    private fun refreshLiveChannelListAndPlay(channelName: String, sourceIndex: Int) {
        refreshingLiveChannelList = true
        pendingLiveRefreshChannelName = channelName
        pendingLiveRefreshSourceIndex = sourceIndex
        currentLiveLookBackIndex = -1
        currentLiveChangeSourceTimes = 0
        allowLiveSwitchPlayer = true
        channelGroupPasswordConfirmed.clear()
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
        mHandler.removeCallbacks(mLoadEpgRun)
        hideSwitchChannelSnapshot()
        tvLeftChannelListLayout?.visibility = View.INVISIBLE
        tvRightSettingLayout?.visibility = View.INVISIBLE
        liveChannelGroupAdapter?.clearGroupState()
        liveChannelItemAdapter?.apply {
            setFocusedChannelIndex(-1)
            setSelectedChannelIndex(-1)
            setNewData(ArrayList())
        }
        initLiveChannelList()
        initLiveSettingGroupList()
    }

    private fun getCurrentLiveApiHistoryIndex(): Int {
        val history: ArrayList<String> = Hawk.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
        if (history.isEmpty()) return -1
        val current = Hawk.get(HawkConfig.LIVE_API_URL, "")
        val idx = history.indexOf(current)
        return if (idx >= 0) idx else -1
    }

    private fun initLiveChannelList() {
        if (ApiConfig.get().shouldReloadLiveConfig()) {
            loadLiveConfigOnEnter()
            return
        }
        val list = ApiConfig.get().getChannelGroupList()
        if (list.isEmpty()) {
            loadLiveConfigOnEnter()
            return
        }
        initLiveObj()
        if (list.size == 1 && (list[0].getGroupName() ?: "").startsWith("http://127.0.0.1")) {
            loadProxyLives(list[0].getGroupName() ?: "")
        } else {
            applyLiveChannelGroups(list)
        }
    }

    private var loadingLiveConfigOnEnter = false

    private fun loadLiveConfigOnEnter() {
        if (loadingLiveConfigOnEnter) return
        loadingLiveConfigOnEnter = true
        showLoading()
        ApiConfig.get().loadLiveConfig(true, object : ApiConfig.LoadConfigCallback {
            override fun success() {
                mHandler.post {
                    loadingLiveConfigOnEnter = false
                    initLiveChannelList()
                    initLiveSettingGroupList()
                }
            }

            override fun error(msg: String) {
                mHandler.post {
                    loadingLiveConfigOnEnter = false
                    setEmptyLiveChannelList()
                }
            }

            override fun notice(msg: String) {
                mHandler.post {
                    Toast.makeText(this@LivePlayActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun loadProxyLives(url: String) {
        var proxyUrl = url
        try {
            val parsedUrl = Uri.parse(proxyUrl)
            val extParam = parsedUrl.getQueryParameter("ext")
            if (extParam != null) {
                proxyUrl = String(Base64.decode(extParam, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            }
        } catch (th: Throwable) {
            if (!proxyUrl.startsWith("http://127.0.0.1")) {
                setEmptyLiveChannelList()
                return
            }
        }
        if (!isValidLiveProxyUrl(proxyUrl)) {
            setEmptyLiveChannelList()
            return
        }
        if (!refreshingLiveChannelList) {
            showLoading()
        }

        LOG.i("echo-live-url:$proxyUrl")

        if (proxyUrl.contains(".py") || proxyUrl.contains(".js")) {
            val finalUrl = proxyUrl
            val waitResponse = Runnable {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable<String> {
                    val sp = ApiConfig.get().getLiveCSP(finalUrl)
                    sp.liveContent(finalUrl)
                })
                var sortJson: String? = null
                try {
                    sortJson = future.get(ApiConfig.get().getLiveConnectTimeoutSeconds().toLong(), TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    e.printStackTrace()
                    future.cancel(true)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                } finally {
                    if (sortJson.isNullOrEmpty()) {
                        mHandler.post { setEmptyLiveChannelList() }
                        return@Runnable
                    }
                    val livesArray = TxtSubscribe.parseToJsonArray(sortJson)
                    ApiConfig.get().loadLives(livesArray)
                    val list = ApiConfig.get().getChannelGroupList()
                    if (list.isEmpty()) {
                        mHandler.post { setEmptyLiveChannelList() }
                        return@Runnable
                    }
                    val loadedGroups = ArrayList<LiveChannelGroup>(list)
                    mHandler.post { applyLiveChannelGroups(loadedGroups) }
                    try {
                        executor.shutdown()
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            }
            Executors.newSingleThreadExecutor().execute(waitResponse)
        } else {
            OkGo.get<String>(proxyUrl).execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()?.string() ?: ""
                }

                override fun onSuccess(response: Response<String>) {
                    val livesArray = TxtSubscribe.parseToJsonArray(response.body())
                    ApiConfig.get().loadLives(livesArray)
                    val list = ApiConfig.get().getChannelGroupList()
                    if (list.isEmpty()) {
                        mHandler.post { setEmptyLiveChannelList() }
                        return
                    }
                    val loadedGroups = ArrayList<LiveChannelGroup>(list)
                    mHandler.post { applyLiveChannelGroups(loadedGroups) }
                }

                override fun onError(response: Response<String>) {
                    mHandler.post { setEmptyLiveChannelList() }
                }
            })
        }
    }

    private fun isValidLiveProxyUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lowerUrl = url.trim().lowercase(Locale.US)
        return lowerUrl.startsWith("http://") ||
                lowerUrl.startsWith("https://") ||
                lowerUrl.startsWith("rtsp://") ||
                lowerUrl.startsWith("rtmp://") ||
                lowerUrl.startsWith("rtp://")
    }

    private fun applyLiveChannelGroups(groups: List<LiveChannelGroup>) {
        liveChannelGroupList.clear()
        liveChannelGroupList.addAll(groups)
        showSuccess()
        initLiveState()
    }

    private fun initLiveState() {
        refreshingLiveChannelList = false
        val lastChannelName = pendingLiveRefreshChannelName ?: Hawk.get(HawkConfig.LIVE_CHANNEL, "")
        val sourceIndex = pendingLiveRefreshSourceIndex
        pendingLiveRefreshChannelName = null
        pendingLiveRefreshSourceIndex = -1

        var lastChannelGroupIndex = -1
        var lastLiveChannelIndex = -1
        var lastLiveChannelItem: LiveChannelItem? = null
        for (liveChannelGroup in liveChannelGroupList) {
            val groupChannels = liveChannelGroup.getLiveChannels()
            if (groupChannels.isNullOrEmpty()) {
                continue
            }
            for (liveChannelItem in groupChannels) {
                if (liveChannelItem.getChannelName() == lastChannelName) {
                    lastChannelGroupIndex = liveChannelGroup.getGroupIndex()
                    lastLiveChannelIndex = liveChannelItem.getChannelIndex()
                    lastLiveChannelItem = liveChannelItem
                    break
                }
            }
            if (lastChannelGroupIndex != -1) break
        }
        if (lastChannelGroupIndex == -1) {
            val cctv1Channel = getFirstChannelByName("CCTV1")
            if (cctv1Channel != null) {
                lastChannelGroupIndex = cctv1Channel[0]
                lastLiveChannelIndex = cctv1Channel[1]
            } else {
                lastChannelGroupIndex = getFirstNoPasswordChannelGroup()
                if (lastChannelGroupIndex == -1) lastChannelGroupIndex = 0
                lastLiveChannelIndex = 0
            }
        }
        if (lastLiveChannelItem != null && sourceIndex >= 0 && lastLiveChannelItem.getSourceNum() > 0) {
            lastLiveChannelItem.setSourceIndex(Math.min(sourceIndex, lastLiveChannelItem.getSourceNum() - 1))
        }

        mVideoView?.let { livePlayerManager.init(it) }
        showTime()
        showNetSpeed()
        tvLeftChannelListLayout?.visibility = View.INVISIBLE
        tvRightSettingLayout?.visibility = View.INVISIBLE
        updateTopStatusVisibility()

        liveChannelGroupAdapter?.clearGroupState()
        liveChannelGroupAdapter?.setNewData(ArrayList(liveChannelGroupList))
        currentLiveChannelIndex = -1
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex)
    }

    private fun isListOrSettingLayoutVisible(): Boolean {
        return tvLeftChannelListLayout?.visibility == View.VISIBLE || tvRightSettingLayout?.visibility == View.VISIBLE
    }

    private fun hasCurrentLiveChannelSource(): Boolean {
        val current = currentLiveChannelItem ?: return false
        val urls = current.getChannelUrls() ?: return false
        return current.getSourceNum() > 0 && current.getSourceIndex() >= 0 && current.getSourceIndex() < urls.size
    }

    private fun getDefaultSettingGroupIndex(): Int {
        if (hasCurrentLiveChannelSource()) return 0
        return if (liveSettingGroupList.size > 6) 6 else 0
    }

    private fun getVisibleLiveSettingGroupList(): ArrayList<LiveSettingGroup> {
        val visibleGroups = ArrayList<LiveSettingGroup>()
        val showChannelOptions = hasCurrentLiveChannelSource()
        for (group in liveSettingGroupList) {
            val groupIndex = group.getGroupIndex()
            if (!showChannelOptions && groupIndex in 0..2) continue
            visibleGroups.add(group)
        }
        return visibleGroups
    }

    private fun initLiveSettingGroupList() {
        liveSettingGroupList = ApiConfig.get().getLiveSettingGroupList().toMutableList()
        if (liveSettingGroupList.size < 7) return
        liveSettingGroupList[3].getLiveSettingItems()?.get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1))?.setItemSelected(true)
        liveSettingGroupList[4].getLiveSettingItems()?.get(0)?.setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false))
        liveSettingGroupList[4].getLiveSettingItems()?.get(1)?.setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false))
        liveSettingGroupList[4].getLiveSettingItems()?.get(2)?.setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
        liveSettingGroupList[4].getLiveSettingItems()?.get(3)?.setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false))
        val liveGroupIndex = ApiConfig.getLiveGroupIndex()
        val g5Items = liveSettingGroupList[5].getLiveSettingItems()
        if (g5Items != null && liveGroupIndex >= 0 && liveGroupIndex < g5Items.size) {
            g5Items[liveGroupIndex].setItemSelected(true)
        }
    }

    private fun loadCurrentSourceList() {
        val liveSettingItemList = ArrayList<LiveSettingItem>()
        val currentSourceNames = currentLiveChannelItem?.getChannelSourceNames()
        if (currentSourceNames != null) {
            for (j in 0 until currentSourceNames.size) {
                val liveSettingItem = LiveSettingItem()
                liveSettingItem.setItemIndex(j)
                liveSettingItem.setItemName(currentSourceNames[j])
                liveSettingItemList.add(liveSettingItem)
            }
        }
        liveSettingGroupList[0].setLiveSettingItems(liveSettingItemList)
    }

    private fun showResolutionAfterChannelSwitch() {
        resolutionInfoPending = true
        resolutionInfoRetryCount = 0
        tvResolution?.apply {
            text = ""
            visibility = View.GONE
        }
        mHandler.removeCallbacks(mHideResolutionInfoRun)
        mHandler.removeCallbacks(mUpdateResolutionInfoRun)
        mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY)
    }

    private fun showResolutionSetting() {
        mHandler.removeCallbacks(mHideResolutionInfoRun)
        mHandler.removeCallbacks(mUpdateResolutionInfoRun)
        if (Hawk.get(HawkConfig.LIVE_SHOW_RESOLUTION, false)) {
            resolutionInfoPending = true
            resolutionInfoRetryCount = 0
            tvResolution?.visibility = View.GONE
            mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY)
        } else {
            showResolutionAfterChannelSwitch()
        }
    }

    private val mHideResolutionInfoRun = Runnable {
        tvResolution?.visibility = View.GONE
    }

    private val mUpdateResolutionInfoRun: Runnable = object : Runnable {
        override fun run() {
            val resView = tvResolution ?: return
            val videoView = mVideoView ?: return
            if (videoView.getCurrentPlayState() != VideoView.STATE_PREPARED &&
                videoView.getCurrentPlayState() != VideoView.STATE_BUFFERED &&
                videoView.getCurrentPlayState() != VideoView.STATE_PLAYING
            ) {
                retryOrHideResolutionInfo()
                return
            }
            val videoSize = videoView.getVideoSize()
            if (videoSize != null && videoSize.size >= 2 && videoSize[0] > 0 && videoSize[1] > 0) {
                updateResolutionText(videoSize[0], videoSize[1])
                return
            }
            retryOrHideResolutionInfo()
        }
    }

    private fun updateResolutionText(width: Int, height: Int) {
        resolutionInfoPending = false
        tvResolution?.text = "$width x $height"
        tvResolution?.visibility = View.VISIBLE
        mHandler.removeCallbacks(mHideResolutionInfoRun)
    }

    private fun retryOrHideResolutionInfo() {
        if (resolutionInfoPending && resolutionInfoRetryCount++ < RESOLUTION_INFO_MAX_RETRY) {
            mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY)
        } else {
            tvResolution?.visibility = View.GONE
        }
    }

    fun showTime() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false) && !isTopStatusObstructed()) {
            mHandler.post(mUpdateTimeRun)
            tvTime?.visibility = View.VISIBLE
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun)
            tvTime?.visibility = View.GONE
        }
    }

    private val mUpdateTimeRun: Runnable = object : Runnable {
        override fun run() {
            val dayDate = Date()
            @SuppressLint("SimpleDateFormat") val df = SimpleDateFormat("HH:mm")
            tvTime?.text = df.format(dayDate)
            mHandler.postDelayed(this, 1000)
        }
    }

    private fun showNetSpeed() {
        mHandler.removeCallbacks(mUpdateNetSpeedRun)
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false) && !isTopStatusObstructed()) {
            mHandler.post(mUpdateNetSpeedRun)
            tvNetSpeed?.visibility = View.VISIBLE
        } else {
            tvNetSpeed?.visibility = View.GONE
        }
    }

    private fun isTopStatusObstructed(): Boolean {
        return (tvRightSettingLayout != null && tvRightSettingLayout?.visibility == View.VISIBLE) ||
                (ll_right_top_loading != null && ll_right_top_loading?.visibility == View.VISIBLE) ||
                (ll_right_top_huikan != null && ll_right_top_huikan?.visibility == View.VISIBLE)
    }

    private fun updateTopStatusVisibility() {
        if (isTopStatusObstructed()) {
            tvTopStatus?.visibility = View.GONE
        } else {
            showTime()
            showNetSpeed()
            tvTopStatus?.visibility = if (tvTime?.visibility == View.VISIBLE || tvNetSpeed?.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        }
    }

    private val mUpdateNetSpeedRun: Runnable = object : Runnable {
        override fun run() {
            val view = mVideoView ?: return
            val speed = PlayerHelper.getDisplaySpeedBps(view.getTcpSpeed(), true)
            tvNetSpeed?.text = speed
            mHandler.postDelayed(this, 1000)
        }
    }

    private fun showPasswordDialog(groupIndex: Int, liveChannelIndex: Int) {
        if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
        }

        val dialog = LivePasswordDialog(this)
        dialog.setOnListener(object : LivePasswordDialog.OnListener {
            override fun onChange(password: String) {
                val groupPwd = liveChannelGroupList[groupIndex].getGroupPassword() ?: ""
                if (password == groupPwd) {
                    channelGroupPasswordConfirmed.add(groupIndex)
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex)
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show()
                }

                if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
                    mHandler.postDelayed(mHideChannelListRun, postTimeout.toLong())
                }
            }

            override fun onCancel() {
                if (tvLeftChannelListLayout?.visibility == View.VISIBLE) {
                    val currentIdx = liveChannelGroupAdapter?.getSelectedGroupIndex() ?: 0
                    liveChannelItemAdapter?.setNewData(getLiveChannels(currentIdx))
                }
            }
        })
        dialog.show()
    }

    private fun loadChannelGroupDataAndPlay(groupIndex: Int, liveChannelIndex: Int) {
        liveChannelGroupAdapter?.setSelectedGroupIndex(groupIndex)
        loadChannelGroupData(groupIndex)

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex)
            mChannelGroupView?.scrollToPosition(groupIndex)
            mLiveChannelView?.scrollToPosition(liveChannelIndex)
        }
    }

    private fun loadChannelGroupData(groupIndex: Int) {
        liveChannelItemAdapter?.setNewData(getLiveChannels(groupIndex))
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1) {
                mLiveChannelView?.scrollToPosition(currentLiveChannelIndex)
            }
            liveChannelItemAdapter?.setSelectedChannelIndex(currentLiveChannelIndex)
        } else {
            mLiveChannelView?.scrollToPosition(0)
            liveChannelItemAdapter?.setSelectedChannelIndex(-1)
        }
    }

    private fun isNeedInputPassword(groupIndex: Int): Boolean {
        val groupPwd = liveChannelGroupList[groupIndex].getGroupPassword()
        return !groupPwd.isNullOrEmpty() && !isPasswordConfirmed(groupIndex)
    }

    private fun isPasswordConfirmed(groupIndex: Int): Boolean {
        for (confirmedNum in channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex) return true
        }
        return false
    }

    private fun getLiveChannels(groupIndex: Int): ArrayList<LiveChannelItem> {
        return if (!isNeedInputPassword(groupIndex)) {
            liveChannelGroupList[groupIndex].getLiveChannels() ?: ArrayList()
        } else {
            ArrayList()
        }
    }

    private fun getFirstChannelByName(keyword: String?): Array<Int>? {
        if (TextUtils.isEmpty(keyword)) return null
        val upperKeyword = keyword!!.uppercase(Locale.US)
        for (liveChannelGroup in liveChannelGroupList) {
            if (isNeedInputPassword(liveChannelGroup.getGroupIndex())) continue
            val groupChannels = liveChannelGroup.getLiveChannels()
            if (groupChannels.isNullOrEmpty()) continue
            for (item in groupChannels) {
                val name = item.getChannelName()
                if (TextUtils.isEmpty(name)) continue
                if (name!!.uppercase(Locale.US).contains(upperKeyword)) {
                    return arrayOf(liveChannelGroup.getGroupIndex(), item.getChannelIndex())
                }
            }
        }
        return null
    }

    private fun getNextChannel(direction: Int): Array<Int> {
        var channelGroupIndex = currentChannelGroupIndex
        var liveChannelIndex = currentLiveChannelIndex

        // 跨选分组模式下跳过加密频道分组（遥控器上下键换台/超时换源）
        if (direction > 0) {
            liveChannelIndex++
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size) {
                liveChannelIndex = 0
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++
                        if (channelGroupIndex >= liveChannelGroupList.size) {
                            channelGroupIndex = 0
                        }
                    } while (!liveChannelGroupList[channelGroupIndex].getGroupPassword().isNullOrEmpty() || channelGroupIndex == currentChannelGroupIndex)
                }
            }
        } else {
            liveChannelIndex--
            if (liveChannelIndex < 0) {
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--
                        if (channelGroupIndex < 0) {
                            channelGroupIndex = liveChannelGroupList.size - 1
                        }
                    } while (!liveChannelGroupList[channelGroupIndex].getGroupPassword().isNullOrEmpty() || channelGroupIndex == currentChannelGroupIndex)
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size - 1
            }
        }

        return arrayOf(channelGroupIndex, liveChannelIndex)
    }

    private fun getFirstNoPasswordChannelGroup(): Int {
        for (liveChannelGroup in liveChannelGroupList) {
            if (liveChannelGroup.getGroupPassword().isNullOrEmpty()) {
                return liveChannelGroup.getGroupIndex()
            }
        }
        return -1
    }

    private fun isCurrentLiveChannelValid(): Boolean {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun durationToString(duration: Int): String {
        var durMs = duration
        if (durMs < 0) {
            durMs = 0
        }
        val result: String
        val dur = durMs / 1000
        val hour = dur / 3600
        val min = (dur / 60) % 60
        val sec = dur % 60
        result = if (hour > 0) {
            if (min > 9) {
                if (sec > 9) {
                    "$hour:$min:$sec"
                } else {
                    "$hour:$min:0$sec"
                }
            } else {
                if (sec > 9) {
                    "$hour:0$min:$sec"
                } else {
                    "$hour:0$min:0$sec"
                }
            }
        } else {
            if (min > 9) {
                if (sec > 9) {
                    "$min:$sec"
                } else {
                    "$min:0$sec"
                }
            } else {
                if (sec > 9) {
                    "0$min:$sec"
                } else {
                    "0$min:0$sec"
                }
            }
        }
        return result
    }

    fun showProgressBars(show: Boolean) {
        sBar?.requestFocus()
        if (show) {
            ll_right_top_huikan?.visibility = View.VISIBLE
            updateTopStatusVisibility()
            backcontroller?.visibility = View.VISIBLE
            ll_epg?.visibility = View.GONE
        } else {
            backcontroller?.visibility = View.GONE
            ll_right_top_huikan?.visibility = View.GONE
            updateTopStatusVisibility()
            if (tip_epg1?.text != "暂无信息") {
                ll_epg?.visibility = View.VISIBLE
            }
        }

        iv_play?.setOnClickListener {
            mVideoView?.start()
            iv_play?.visibility = View.INVISIBLE
            countDownTimer?.start()
            iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
        }

        iv_playpause?.setOnClickListener {
            if (mVideoView?.isPlaying() == true) {
                mVideoView?.pause()
                countDownTimer?.cancel()
                iv_play?.visibility = View.VISIBLE
                iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.icon_play)
            } else {
                mVideoView?.start()
                iv_play?.visibility = View.INVISIBLE
                countDownTimer?.start()
                iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
            }
        }

        sBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(arg0: SeekBar?) {}
            override fun onStartTrackingTouch(arg0: SeekBar?) {}
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromuser: Boolean) {
                if (fromuser) {
                    if (countDownTimer != null) {
                        mVideoView?.seekTo(progress.toLong())
                        countDownTimer?.cancel()
                        countDownTimer?.start()
                    }
                }
            }
        })

        sBar?.setOnKeyListener { _, keycode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keycode == KeyEvent.KEYCODE_DPAD_CENTER || keycode == KeyEvent.KEYCODE_ENTER) {
                    if (mVideoView?.isPlaying() == true) {
                        mVideoView?.pause()
                        countDownTimer?.cancel()
                        iv_play?.visibility = View.VISIBLE
                        iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.icon_play)
                    } else {
                        mVideoView?.start()
                        iv_play?.visibility = View.INVISIBLE
                        countDownTimer?.start()
                        iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
                    }
                }
            }
            false
        }

        if (mVideoView?.isPlaying() == true) {
            iv_play?.visibility = View.INVISIBLE
            iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.vod_pause)
        } else {
            iv_play?.visibility = View.VISIBLE
            iv_playpause?.background = ContextCompat.getDrawable(context ?: this, R.drawable.icon_play)
        }

        if (countDownTimer3 == null) {
            countDownTimer3 = object : CountDownTimer(postTimeout.toLong(), 1000) {
                override fun onTick(arg0: Long) {
                    val view = mVideoView
                    if (view != null) {
                        sBar?.progress = safeTimeMs(view.getCurrentPosition())
                        tv_currentpos?.text = durationToString(safeTimeMs(view.getCurrentPosition()))
                    }
                }

                override fun onFinish() {
                    if (backcontroller?.visibility == View.VISIBLE) {
                        backcontroller?.visibility = View.GONE
                    }
                }
            }
        } else {
            countDownTimer3?.cancel()
        }
        countDownTimer3?.start()
    }

    /**
     * 当播放列表为空或加载失败时，设置一个默认的播放列表，保证播放界面不会崩溃
     */
    private fun clearLiveChannelList() {
        clearLiveChannelList(true)
    }

    private fun clearLiveChannelList(releasePlayer: Boolean) {
        refreshingLiveChannelList = false
        pendingLiveRefreshChannelName = null
        pendingLiveRefreshSourceIndex = -1
        currentLiveChannelItem = null
        currentLiveChannelIndex = -1
        currentLiveLookBackIndex = -1
        currentLiveChangeSourceTimes = 0
        liveChannelGroupList.clear()
        (ApiConfig.get().getChannelGroupList() as? MutableList<*>)?.clear()
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
        mHandler.removeCallbacks(mLoadEpgRun)
        hideSwitchChannelSnapshot()
        if (releasePlayer && mVideoView != null) mVideoView?.release()
        showSuccess()
        liveChannelGroupAdapter?.apply {
            clearGroupState()
            setNewData(ArrayList())
        }
        liveChannelItemAdapter?.apply {
            setFocusedChannelIndex(-1)
            setSelectedChannelIndex(-1)
            setNewData(ArrayList())
        }
        tvLeftChannelListLayout?.visibility = View.INVISIBLE
        tvRightSettingLayout?.visibility = View.INVISIBLE
    }

    private fun setEmptyLiveChannelList() {
        setEmptyLiveChannelList(true)
    }

    private fun setEmptyLiveChannelList(releasePlayer: Boolean) {
        clearLiveChannelList(releasePlayer)
    }
}
