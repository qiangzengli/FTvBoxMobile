package com.github.tvbox.osc.player.controller

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.bean.ParseBean
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.server.RemoteServer
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView
import com.github.tvbox.osc.ui.adapter.ParseAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.M3u8
import com.github.tvbox.osc.util.PlayerHelper
import com.github.tvbox.osc.util.ScreenUtils
import com.github.tvbox.osc.util.SubtitleHelper
import com.github.tvbox.osc.util.VideoParseRuler
import com.github.tvbox.osc.util.thunder.Jianpian
import com.github.tvbox.osc.util.thunder.Thunder
import com.google.android.exoplayer2.ui.SubtitleView
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.HttpHeaders
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xwalk.core.XWalkView
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils.safeTimeMs
import xyz.doikki.videoplayer.util.PlayerUtils.stringForTime
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Locale

class VodController(context: Context) : BaseController(context) {

    private val PORTRAIT_EPISODE_SWIPE_DP = 80f
    private val PORTRAIT_EPISODE_TITLE_SHOW_MS = 3000L
    private var portraitEpisodeSwipeTriggered = false
    private val portraitEpisodeTitleRunnable = Runnable {
        if (!isBottomVisible()) mTopRoot1?.visibility = GONE
    }

    @JvmField
    var mSeekBar: SeekBar? = null
    @JvmField
    var mCurrentTime: TextView? = null
    @JvmField
    var mTotalTime: TextView? = null
    @JvmField
    var mIsDragging = false
    private var mSeekBarKeyTracking = false
    @JvmField
    var mProgressRoot: LinearLayout? = null
    @JvmField
    var mProgressText: TextView? = null
    @JvmField
    var mProgressIcon: ImageView? = null
    @JvmField
    var mLockView: ImageView? = null
    @JvmField
    var mBottomRoot: LinearLayout? = null
    @JvmField
    var mPlayBtnGroup: LinearLayout? = null
    @JvmField
    var mTopRoot1: LinearLayout? = null
    @JvmField
    var mTopRoot2: LinearLayout? = null
    @JvmField
    var mParseRoot: LinearLayout? = null
    @JvmField
    var mGridParseView: TvRecyclerView? = null
    @JvmField
    var mPlayTitle: TextView? = null
    @JvmField
    var mPlayTitle1: TextView? = null
    @JvmField
    var mPlayLabel: TextView? = null
    @JvmField
    var mPlayLoadNetSpeedRightTop: TextView? = null
    @JvmField
    var mNextBtn: TextView? = null
    @JvmField
    var mPreBtn: TextView? = null
    @JvmField
    var mPlayerScaleBtn: TextView? = null
    @JvmField
    var mPlayerSpeedBtn: TextView? = null
    @JvmField
    var mPlayerBtn: TextView? = null
    @JvmField
    var mPlayerIJKBtn: TextView? = null
    @JvmField
    var mPlayerRetry: TextView? = null
    @JvmField
    var mPlayrefresh: TextView? = null
    @JvmField
    var mPlayerTimeStartEndText: TextView? = null
    @JvmField
    var mPlayerTimeStartBtn: TextView? = null
    @JvmField
    var mPlayerTimeSkipBtn: TextView? = null
    @JvmField
    var mPlayerTimeResetBtn: TextView? = null
    @JvmField
    var mPlayPauseTime: TextView? = null
    @JvmField
    var mPlayLoadNetSpeed: TextView? = null
    @JvmField
    var mVideoSize: TextView? = null
    @JvmField
    var mSubtitleView: SimpleSubtitleView? = null
    @JvmField
    var mLyricView: SimpleSubtitleView? = null
    @JvmField
    var mExoSubtitleView: SubtitleView? = null
    @JvmField
    var mZimuBtn: TextView? = null
    @JvmField
    var mAudioTrackBtn: TextView? = null
    @JvmField
    var mVideoTrackBtn: TextView? = null
    @JvmField
    var mDanmuSettingBtn: TextView? = null
    @JvmField
    var mDanmuSearchUiBtn: TextView? = null
    @JvmField
    var mLandscapePortraitBtn: TextView? = null
    private var backBtn: View? = null
    private var isClickBackBtn = false
    @JvmField
    var seekTime: TextView? = null
    @JvmField
    var mScreenDisplay: TextView? = null
    @JvmField
    var tv_screen_display: LinearLayout? = null
    @JvmField
    var mCastBtn: TextView? = null
    @JvmField
    var net_play_speed: TextView? = null
    private var hasDanmu = false
    private var showParse = false

    @JvmField
    var lockRunnable = LockRunnable()
    private var isLock = false
    private var previewMode = false
    @JvmField
    var myHandle: Handler? = null
    @JvmField
    var myRunnable: Runnable? = null
    @JvmField
    var myHandleSeconds = 10000

    @JvmField
    var videoPlayState = 0

    private val myRunnable2: Runnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val timeFormat = SimpleDateFormat("HH:mm")
            mPlayPauseTime?.text = timeFormat.format(date)
            val mSpeed = mControlWrapper.getTcpSpeed()
            val speed = PlayerHelper.getDisplaySpeed(mSpeed, false)
            val speedBps = PlayerHelper.getDisplaySpeedBps(mSpeed, true)
            mPlayLoadNetSpeedRightTop?.text = speedBps
            mPlayLoadNetSpeed?.text = speed
            net_play_speed?.text = speedBps
            val mVideoSizes = mControlWrapper.getVideoSize()
            val width = mVideoSizes[0].toString()
            val height = mVideoSizes[1].toString()
            mVideoSize?.text = "$width X $height"

            mHandler.postDelayed(this, 1000)
        }
    }

    private var listener: VodControlListener? = null
    private var skipEnd = true
    private var mPlayerConfig: JSONObject? = null
    private var simSlideStart = false
    private var simSeekPosition = 0
    private var simSlideOffset = 0L
    private var lastSlideTime = 0L
    private var fromLongPress = false
    private var speed_old = 1.0f

    private val mmHandler = Handler(Looper.getMainLooper())
    private var mLongPressRunnable: Runnable? = null
    private val LONG_PRESS_DELAY = ViewConfiguration.getLongPressTimeout().toLong()
    private var isLongPressTriggered = false

    init {
        mHandlerCallback = HandlerCallback { msg ->
            when (msg.what) {
                1000 -> { // seek 刷新
                    mProgressRoot?.visibility = VISIBLE
                }
                1001 -> { // seek 关闭
                    mProgressRoot?.visibility = GONE
                }
                1002 -> { // 显示底部菜单
                    updateDanmuSearchUiBtn()
                    hidePauseRoot()
                    mPlayTitle?.visibility = GONE
                    mBottomRoot?.visibility = VISIBLE
                    mTopRoot1?.visibility = VISIBLE
                    mTopRoot2?.visibility = VISIBLE
                    mPlayLoadNetSpeedRightTop?.visibility = VISIBLE
                    if (Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE) == GONE) {
                        mPlayPauseTime?.visibility = VISIBLE
                    } else {
                        net_play_speed?.visibility = GONE
                    }
                    val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    backBtn?.visibility = if (ScreenUtils.isTv(context) || isPortrait) INVISIBLE else VISIBLE
                    showLockView()
                }
                1003 -> { // 隐藏底部菜单
                    mBottomRoot?.visibility = GONE
                    mTopRoot1?.visibility = GONE
                    mPlayLoadNetSpeedRightTop?.visibility = GONE
                    if (videoPlayState == VideoView.STATE_PAUSED) {
                        showPauseRoot()
                        mPlayTitle?.visibility = VISIBLE
                    } else {
                        hidePauseRoot()
                        mPlayTitle?.visibility = GONE
                    }
                    if (Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE) == GONE) {
                        mPlayPauseTime?.visibility = GONE
                    } else {
                        net_play_speed?.visibility = VISIBLE
                    }
                    backBtn?.visibility = INVISIBLE
                    mHandler.removeCallbacks(lockRunnable)
                    mLockView?.visibility = INVISIBLE
                }
                1004 -> { // 设置速度
                    if (isInPlaybackState()) {
                        try {
                            val speed = (mPlayerConfig?.getDouble("sp") ?: 1.0).toFloat()
                            mControlWrapper.setSpeed(speed)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    } else {
                        mHandler.sendEmptyMessageDelayed(1004, 100)
                    }
                }
            }
        }
    }

    override fun onDown(e: MotionEvent): Boolean {
        portraitEpisodeSwipeTriggered = false
        return super.onDown(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (isPortraitEpisodeSwipe(e1, e2)) {
            if (!portraitEpisodeSwipeTriggered && Math.abs(e2.y - (e1?.y ?: 0f)) >= portraitEpisodeSwipeThreshold()) {
                portraitEpisodeSwipeTriggered = true
                if (listener != null) {
                    if (e2.y < (e1?.y ?: 0f)) listener?.playNext(false)
                    else listener?.playPre()
                    showPortraitEpisodeTitle()
                }
            }
            return true
        }
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    private fun isPortraitEpisodeSwipe(e1: MotionEvent?, e2: MotionEvent?): Boolean {
        if (e1 == null || e2 == null || !canHandleGesture(e1)) return false
        if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return false
        return Math.abs(e2.y - e1.y) > Math.abs(e2.x - e1.x)
    }

    private fun portraitEpisodeSwipeThreshold(): Float {
        return resources.displayMetrics.density * PORTRAIT_EPISODE_SWIPE_DP
    }

    private fun showPortraitEpisodeTitle() {
        if (isBottomVisible()) return
        mHandler.removeCallbacks(portraitEpisodeTitleRunnable)
        mTopRoot1?.visibility = VISIBLE
        mHandler.postDelayed(portraitEpisodeTitleRunnable, PORTRAIT_EPISODE_TITLE_SHOW_MS)
    }

    private fun showLockView() {
        if (previewMode || resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) isLock = false
            mHandler.removeCallbacks(lockRunnable)
            mLockView?.visibility = INVISIBLE
            return
        }
        mLockView?.visibility = if (ScreenUtils.isTv(context)) INVISIBLE else VISIBLE
        mHandler.removeCallbacks(lockRunnable)
        if (isLock) {
            mHandler.postDelayed(lockRunnable, 3000)
        }
    }

    fun setPreviewMode(previewMode: Boolean) {
        this.previewMode = previewMode
        mHandler.removeCallbacks(lockRunnable)
        mLockView?.visibility = INVISIBLE
    }

    override fun initView() {
        super.initView()
        mCurrentTime = findViewById(R.id.curr_time)
        mTotalTime = findViewById(R.id.total_time)
        mPlayTitle = findViewById(R.id.tv_info_name)
        mPlayTitle1 = findViewById(R.id.tv_info_name1)
        mPlayLabel = findViewById(R.id.play_label)
        mPlayLoadNetSpeedRightTop = findViewById(R.id.tv_play_load_net_speed_right_top)
        mSeekBar = findViewById(R.id.seekBar)
        val seekThumb = CircleThumbDrawable(context)
        mSeekBar?.thumb = seekThumb
        mSeekBar?.thumbOffset = seekThumb.intrinsicWidth / 2
        mProgressRoot = findViewById(R.id.tv_progress_container)
        mProgressIcon = findViewById(R.id.tv_progress_icon)
        mProgressText = findViewById(R.id.tv_progress_text)
        mBottomRoot = findViewById(R.id.bottom_container)
        mTopRoot1 = findViewById(R.id.tv_top_l_container)
        mTopRoot2 = findViewById(R.id.tv_top_r_container)
        mPlayBtnGroup = findViewById(R.id.play_btn_group)
        tv_screen_display = findViewById(R.id.tv_screen_display)
        net_play_speed = findViewById(R.id.net_play_speed)
        mParseRoot = findViewById(R.id.parse_root)
        mGridParseView = findViewById(R.id.mGridParseView)
        mPlayerRetry = findViewById(R.id.play_retry)
        mPlayrefresh = findViewById(R.id.play_refresh)
        mNextBtn = findViewById(R.id.play_next)
        mPreBtn = findViewById(R.id.play_pre)
        mPlayerScaleBtn = findViewById(R.id.play_scale)
        mPlayerSpeedBtn = findViewById(R.id.play_speed)
        mPlayerBtn = findViewById(R.id.play_player)
        mPlayerIJKBtn = findViewById(R.id.play_ijk)
        mPlayerTimeStartEndText = findViewById(R.id.play_time_start_end_text)
        mPlayerTimeStartBtn = findViewById(R.id.play_time_start)
        mPlayerTimeSkipBtn = findViewById(R.id.play_time_end)
        mPlayerTimeResetBtn = findViewById(R.id.play_time_reset)
        mPlayPauseTime = findViewById(R.id.tv_sys_time)
        mPlayLoadNetSpeed = findViewById(R.id.tv_play_load_net_speed)
        mVideoSize = findViewById(R.id.tv_videosize)
        mSubtitleView = findViewById(R.id.subtitle_view)
        mLyricView = findViewById(R.id.lyric_view)
        mExoSubtitleView = findViewById(R.id.exo_subtitle_view)
        mZimuBtn = findViewById(R.id.zimu_select)
        mAudioTrackBtn = findViewById(R.id.audio_track_select)
        mVideoTrackBtn = findViewById(R.id.video_track_select)
        mDanmuSettingBtn = findViewById(R.id.danmu_setting)
        mDanmuSearchUiBtn = findViewById(R.id.danmu_search_ui)
        mLandscapePortraitBtn = findViewById(R.id.landscape_portrait)
        backBtn = findViewById(R.id.tv_back)
        seekTime = findViewById(R.id.tv_seek_time)
        mScreenDisplay = findViewById(R.id.screen_display)
        mCastBtn = findViewById(R.id.play_cast)
        updateDanmuBtn()
        updateDanmuSearchUiBtn()
        backBtn?.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLandscapePortrait()
                hideBottom()
                return@setOnClickListener
            }
            if (context is Activity) {
                isClickBackBtn = true
                (context as Activity).onBackPressed()
            }
        }
        mLockView = findViewById(R.id.tv_lock)
        mLockView?.setOnClickListener {
            isLock = !isLock
            mLockView?.setImageResource(if (isLock) R.drawable.icon_lock else R.drawable.icon_unlock)
            if (isLock) {
                val obtain = Message.obtain()
                obtain.what = 1003
                mHandler.sendMessage(obtain)
            }
            showLockView()
        }
        val rootView = findViewById<View>(R.id.rootView)
        rootView?.setOnTouchListener { _, event ->
            if (previewMode) {
                return@setOnTouchListener false
            }
            if (isLock) {
                if (event.action == MotionEvent.ACTION_UP) {
                    showLockView()
                }
            }
            isLock
        }

        initSubtitleInfo()

        myHandle = Handler(Looper.getMainLooper())
        myRunnable = Runnable { hideBottom() }

        mPlayPauseTime?.post { mHandler.post(myRunnable2) }

        mGridParseView?.layoutManager = V7LinearLayoutManager(context, 0, false)
        val parseAdapter = ParseAdapter()
        parseAdapter.setOnItemClickListener { adapter, view, position ->
            val parseBean = parseAdapter.getItem(position) ?: return@setOnItemClickListener
            val currentDefault = parseAdapter.data.indexOf(ApiConfig.get().getDefaultParse())
            parseAdapter.notifyItemChanged(currentDefault)
            ApiConfig.get().setDefaultParse(parseBean)
            parseAdapter.notifyItemChanged(position)
            listener?.changeParse(parseBean)
            hideBottom()
        }
        mGridParseView?.adapter = parseAdapter
        parseAdapter.setNewData(ApiConfig.get().getParseBeanList())

        mSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                updateSeekBarTime(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                beginSeekBarTracking()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                finishSeekBarTracking()
            }
        })
        mSeekBar?.setOnHoverListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    keepSeekBarActive()
                    v.isSelected = true
                    v.refreshDrawableState()
                    v.jumpDrawablesToCurrentState()
                    v.invalidate()
                }
                MotionEvent.ACTION_HOVER_MOVE -> {
                    keepSeekBarActive()
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    v.isSelected = false
                    v.refreshDrawableState()
                    v.jumpDrawablesToCurrentState()
                    v.invalidate()
                }
            }
            false
        }
        mSeekBar?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val hasParse = mParseRoot?.visibility == VISIBLE && mGridParseView?.adapter != null && mGridParseView!!.adapter!!.itemCount > 0
                mSeekBar?.nextFocusUpId = if (hasParse) R.id.mGridParseView else R.id.play_next
                keepSeekBarActive()
                v.isSelected = true
                v.refreshDrawableState()
                v.jumpDrawablesToCurrentState()
                v.invalidate()
            } else {
                v.isSelected = false
                v.refreshDrawableState()
                v.jumpDrawablesToCurrentState()
                v.invalidate()
                if (mSeekBarKeyTracking) {
                    finishSeekBarTracking()
                }
            }
        }
        mSeekBar?.setOnKeyListener { v, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener false
            }
            if (!isInPlaybackState()) {
                return@setOnKeyListener true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                beginSeekBarTracking()
                mSeekBarKeyTracking = true
                v.isSelected = true
                v.jumpDrawablesToCurrentState()
                v.invalidate()
                moveSeekBarByKey(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                return@setOnKeyListener true
            } else if (event.action == KeyEvent.ACTION_UP) {
                v.isSelected = v.hasFocus()
                v.jumpDrawablesToCurrentState()
                v.invalidate()
                if (mSeekBarKeyTracking) {
                    finishSeekBarTracking()
                }
                return@setOnKeyListener true
            }
            true
        }
        mSeekBar?.setOnGenericMotionListener { _, event ->
            if (event.action != MotionEvent.ACTION_SCROLL || !isInPlaybackState()) {
                return@setOnGenericMotionListener false
            }
            var scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (scroll == 0f) {
                scroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            }
            if (scroll == 0f) {
                return@setOnGenericMotionListener false
            }
            beginSeekBarTracking()
            moveSeekBarByKey(if (scroll > 0) 1 else -1)
            finishSeekBarTracking()
            true
        }
        mPlayerRetry?.setOnClickListener {
            listener?.replay(true)
            hideBottom()
        }
        mPlayrefresh?.setOnClickListener {
            listener?.replay(false)
            hideBottom()
        }
        mNextBtn?.setOnClickListener {
            listener?.playNext(false)
            hideBottom()
        }
        setOnTvLongClickListener(mNextBtn) { view ->
            FastClickCheckUtil.check(view)
            listener?.showEpisodeDialog()
            hideBottom()
            true
        }
        mPreBtn?.setOnClickListener {
            listener?.playPre()
            hideBottom()
        }
        setOnTvLongClickListener(mPreBtn) { view ->
            FastClickCheckUtil.check(view)
            listener?.showEpisodeDialog()
            hideBottom()
            true
        }
        mPlayerScaleBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            showScaleDialog()
        }
        setOnTvLongClickListener(mPlayerScaleBtn) { view ->
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            FastClickCheckUtil.check(view)
            try {
                mPlayerConfig?.put("sc", 0)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                mControlWrapper.setScreenScaleType(0)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            true
        }
        mPlayerSpeedBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            showSpeedDialog()
        }
        setOnTvLongClickListener(mPlayerSpeedBtn) { view ->
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            FastClickCheckUtil.check(view)
            try {
                mPlayerConfig?.put("sp", 1.0)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                speed_old = 1.0f
                mControlWrapper.setSpeed(1.0f)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            true
        }
        mPlayerBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            try {
                var playerType = mPlayerConfig?.getInt("pl") ?: 0
                val exsitPlayerTypes = PlayerHelper.getExistPlayerTypes()
                var playerTypeIdx = 0
                val playerTypeSize = exsitPlayerTypes.size
                for (i in 0 until playerTypeSize) {
                    if (playerType == exsitPlayerTypes[i]) {
                        if (i == playerTypeSize - 1) {
                            playerTypeIdx = 0
                        } else {
                            playerTypeIdx = i + 1
                        }
                    }
                }
                playerType = exsitPlayerTypes[playerTypeIdx]
                mPlayerConfig?.put("pl", playerType)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                listener?.replay(false)
                listener?.setAllowSwitchPlayer(false)
                hideBottom()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        setOnTvLongClickListener(mPlayerBtn) { view ->
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            FastClickCheckUtil.check(view)
            try {
                val playerType = mPlayerConfig?.getInt("pl") ?: 0
                var defaultPos = 0
                val players = PlayerHelper.getExistPlayerTypes()
                val renders = ArrayList<Int>()
                for (p in players.indices) {
                    renders.add(p)
                    if (players[p] == playerType) {
                        defaultPos = p
                    }
                }
                val act = mActivity ?: return@setOnTvLongClickListener true
                val dialog = SelectDialog<Int>(act)
                dialog.setTip("请选择播放器")
                dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                    override fun click(value: Int, pos: Int) {
                        try {
                            dialog.cancel()
                            val thisPlayType = players[pos]
                            if (thisPlayType != playerType) {
                                mPlayerConfig?.put("pl", thisPlayType)
                                updatePlayerCfgView()
                                listener?.updatePlayerCfg()
                                listener?.replay(false)
                                listener?.setAllowSwitchPlayer(false)
                                hideBottom()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun getDisplay(valItem: Int): String {
                        val pType = players[valItem]
                        return PlayerHelper.getPlayerName(pType)
                    }
                }, object : DiffUtil.ItemCallback<Int>() {
                    override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                        return oldItem == newItem
                    }

                    override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                        return oldItem == newItem
                    }
                }, renders, defaultPos)
                dialog.show()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            true
        }
        mPlayerIJKBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            try {
                var ijk = mPlayerConfig?.getString("ijk") ?: ""
                val codecs = ApiConfig.get().getIjkCodes() ?: return@setOnClickListener
                for (i in codecs.indices) {
                    if (ijk == codecs[i].getName()) {
                        if (i >= codecs.size - 1) {
                            ijk = codecs[0].getName() ?: ""
                        } else {
                            ijk = codecs[i + 1].getName() ?: ""
                        }
                        break
                    }
                }
                mPlayerConfig?.put("ijk", ijk)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                listener?.replay(false)
                hideBottom()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        mPlayerTimeResetBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            try {
                mPlayerConfig?.put("et", 0)
                mPlayerConfig?.put("st", 0)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        mPlayerTimeStartBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            try {
                val current = safeTimeMs(mControlWrapper.getCurrentPosition())
                val duration = safeTimeMs(mControlWrapper.getDuration())
                if (current > duration / 2) return@setOnClickListener
                mPlayerConfig?.put("st", current / 1000)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        setOnTvLongClickListener(mPlayerTimeStartBtn) {
            try {
                mPlayerConfig?.put("st", 0)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            true
        }
        mPlayerTimeSkipBtn?.setOnClickListener {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            try {
                val current = safeTimeMs(mControlWrapper.getCurrentPosition())
                val duration = safeTimeMs(mControlWrapper.getDuration())
                if (current < duration / 2) return@setOnClickListener
                mPlayerConfig?.put("et", (duration - current) / 1000)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        setOnTvLongClickListener(mPlayerTimeSkipBtn) {
            try {
                mPlayerConfig?.put("et", 0)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            true
        }
        mZimuBtn?.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            listener?.selectSubtitle()
            hideBottom()
        }
        setOnTvLongClickListener(mZimuBtn) {
            mSubtitleView?.visibility = View.GONE
            mSubtitleView?.destroy()
            mSubtitleView?.clearSubtitleCache()
            mSubtitleView?.isInternal = false
            mLyricView?.visibility = View.GONE
            mLyricView?.destroy()
            mLyricView?.clearSubtitleCache()
            mExoSubtitleView?.visibility = View.GONE
            hideBottom()
            Toast.makeText(context, "字幕已关闭", Toast.LENGTH_SHORT).show()
            true
        }
        mAudioTrackBtn?.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            listener?.selectAudioTrack()
            hideBottom()
        }
        mVideoTrackBtn?.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            listener?.selectVideoTrack()
            hideBottom()
        }
        mDanmuSettingBtn?.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            listener?.showDanmuSetting()
        }
        setOnTvLongClickListener(mDanmuSettingBtn) { view ->
            FastClickCheckUtil.check(view)
            val opened = listener?.toggleDanmu() ?: false
            hideBottom()
            Toast.makeText(context, if (opened) "弹幕已开启" else "弹幕已临时关闭", Toast.LENGTH_SHORT).show()
            true
        }
        mDanmuSearchUiBtn?.setOnClickListener {
            listener?.searchDanmuUi(false)
            hideBottom()
        }
        setOnTvLongClickListener(mDanmuSearchUiBtn) {
            listener?.searchDanmuUi(true)
            hideBottom()
            true
        }
        mLandscapePortraitBtn?.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            setLandscapePortrait()
            hideBottom()
        }
        val disPlay = Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE)
        mTopRoot2?.visibility = disPlay
        seekTime?.visibility = disPlay
        net_play_speed?.visibility = disPlay
        mPlayPauseTime?.visibility = disPlay
        mScreenDisplay?.setTextColor(if (disPlay == VISIBLE) resources.getColor(R.color.color_02F8E1) else Color.WHITE)
        mScreenDisplay?.setOnClickListener {
            val nextDisPlay = if (Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE) == VISIBLE) GONE else VISIBLE
            seekTime?.visibility = nextDisPlay
            net_play_speed?.visibility = nextDisPlay
            if (nextDisPlay == VISIBLE) mPlayPauseTime?.visibility = nextDisPlay
            Hawk.put(HawkConfig.SCREEN_DISPLAY, nextDisPlay)
            mScreenDisplay?.setTextColor(if (nextDisPlay == VISIBLE) resources.getColor(R.color.color_02F8E1) else Color.WHITE)
            hideBottom()
        }
        mCastBtn?.setOnClickListener {
            listener?.clickCast()
        }
        if (Build.VERSION.SDK_INT < 30) {
            mCastBtn?.visibility = GONE
        } else {
            mCastBtn?.visibility = VISIBLE
        }
        mScreenDisplay?.nextFocusRightId = R.id.play_next
        mNextBtn?.nextFocusLeftId = R.id.screen_display
        com.fbox.mobile.MobilePlayerControls.install(this)
    }

    private fun showScaleDialog() {
        try {
            val scaleType = mPlayerConfig?.getInt("sc") ?: 0
            val scales = ArrayList<Int>()
            for (i in 0..5) scales.add(i)
            val act = mActivity ?: return
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择画面尺寸")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    try {
                        dialog.cancel()
                        mPlayerConfig?.put("sc", value)
                        updatePlayerCfgView()
                        listener?.updatePlayerCfg()
                        mControlWrapper.setScreenScaleType(value)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }

                override fun getDisplay(value: Int): String {
                    return PlayerHelper.getScaleName(value)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, scales, scaleType)
            dialog.show()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showSpeedDialog() {
        try {
            val speed = (mPlayerConfig?.getDouble("sp") ?: 1.0).toFloat()
            val speeds = ArrayList<Float>()
            val speedOptions = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
            for (value in speedOptions) speeds.add(value)
            val defaultPos = speeds.indexOf(speed)
            val act = mActivity ?: return
            val dialog = SelectDialog<Float>(act)
            dialog.setTip("请选择播放倍速")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Float> {
                override fun click(value: Float, pos: Int) {
                    try {
                        dialog.cancel()
                        mPlayerConfig?.put("sp", value.toDouble())
                        updatePlayerCfgView()
                        listener?.updatePlayerCfg()
                        speed_old = value
                        mControlWrapper.setSpeed(value)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }

                override fun getDisplay(value: Float): String {
                    return "${value}x"
                }
            }, object : DiffUtil.ItemCallback<Float>() {
                override fun areItemsTheSame(oldItem: Float, newItem: Float): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Float, newItem: Float): Boolean {
                    return oldItem == newItem
                }
            }, speeds, if (defaultPos < 0) 1 else defaultPos)
            dialog.show()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun hideLiveAboutBtn() {
        if (mControlWrapper != null && mControlWrapper.getDuration() == 0L) {
            mPlayerSpeedBtn?.visibility = GONE
            mPlayerTimeStartEndText?.visibility = GONE
            mPlayerTimeStartBtn?.visibility = GONE
            mPlayerTimeSkipBtn?.visibility = GONE
            mPlayerTimeResetBtn?.visibility = GONE
        } else {
            mPlayerSpeedBtn?.visibility = View.VISIBLE
            mPlayerTimeStartEndText?.visibility = GONE
            mPlayerTimeStartBtn?.visibility = View.VISIBLE
            mPlayerTimeSkipBtn?.visibility = View.VISIBLE
            mPlayerTimeResetBtn?.visibility = View.VISIBLE
        }
    }

    fun initLandscapePortraitBtnInfo() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        mCurrentTime?.visibility = if (isPortrait) GONE else VISIBLE
        mTotalTime?.visibility = if (isPortrait) GONE else VISIBLE
        if (isPortrait) {
            backBtn?.visibility = INVISIBLE
            mLockView?.visibility = INVISIBLE
        }
        updatePortraitMenu(isPortrait)

        val btn = mLandscapePortraitBtn ?: return
        var showButton = false
        val act = mActivity
        if (mControlWrapper != null && act != null && !ScreenUtils.isTv(act)) {
            val videoSize = mControlWrapper.getVideoSize()
            val width = videoSize[0]
            val height = videoSize[1]
            val duration = safeTimeMs(mControlWrapper.getDuration()).toLong()
            val shortVideo = duration in 1..719999
            showButton = width > 0 && height > 0 && (width <= height || shortVideo) && ScreenUtils.getSqrt(act) < 10.0
        }
        btn.visibility = if (showButton) VISIBLE else GONE
        if (showButton) btn.text = if (isPortrait) "横屏" else "竖屏"
    }

    private fun updatePortraitMenu(isPortrait: Boolean) {
        if (isPortrait) {
            mParseRoot?.visibility = GONE
            mPlayLabel?.visibility = GONE
            mPlayrefresh?.visibility = GONE
            mPlayerScaleBtn?.visibility = GONE
            mPlayerIJKBtn?.visibility = GONE
            mPlayerTimeStartEndText?.visibility = GONE
            mPlayerTimeStartBtn?.visibility = GONE
            mPlayerTimeSkipBtn?.visibility = GONE
            mPlayerTimeResetBtn?.visibility = GONE
            mCastBtn?.visibility = GONE
            mZimuBtn?.visibility = GONE
            mAudioTrackBtn?.visibility = GONE
            mVideoTrackBtn?.visibility = GONE
            mDanmuSettingBtn?.visibility = GONE
            mDanmuSearchUiBtn?.visibility = GONE
            mScreenDisplay?.visibility = GONE
            return
        }

        mParseRoot?.visibility = if (showParse) VISIBLE else GONE
        mPlayrefresh?.visibility = VISIBLE
        mPlayerScaleBtn?.visibility = VISIBLE
        mPlayerTimeStartEndText?.visibility = GONE
        mPlayerTimeStartBtn?.visibility = VISIBLE
        mPlayerTimeSkipBtn?.visibility = VISIBLE
        mPlayerTimeResetBtn?.visibility = VISIBLE
        mZimuBtn?.visibility = VISIBLE
        mScreenDisplay?.visibility = VISIBLE
        mCastBtn?.visibility = if (Build.VERSION.SDK_INT < 30) GONE else VISIBLE
        if (mPlayerConfig != null) updatePlayerCfgView()
        updateDanmuBtn()
        updateDanmuSearchUiBtn()
    }

    fun setLandscapePortrait() {
        val act = mActivity ?: return
        if (act.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLandscapePortraitBtn?.text = "竖屏"
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            mLandscapePortraitBtn?.text = "横屏"
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initLandscapePortraitBtnInfo()
    }

    fun initSubtitleInfo() {
        val act = mActivity ?: return
        val subtitleTextSize = SubtitleHelper.getTextSize(act)
        mSubtitleView?.setTextSize(subtitleTextSize.toFloat())
    }

    override fun getLayoutId(): Int {
        return R.layout.player_vod_control_view
    }

    fun showParse(userJxList: Boolean) {
        showParse = userJxList
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        mParseRoot?.visibility = if (showParse && !isPortrait) VISIBLE else GONE
    }

    fun setPlayerConfig(playerCfg: JSONObject?) {
        this.mPlayerConfig = playerCfg
        updatePlayerCfgView()
    }

    fun updatePlayerCfgView() {
        try {
            val cfg = mPlayerConfig ?: return
            val playerType = cfg.getInt("pl")
            mPlayerBtn?.text = getPlayerShortName(playerType)
            mPlayerScaleBtn?.text = PlayerHelper.getScaleName(cfg.getInt("sc"))
            mPlayerIJKBtn?.text = getCodecShortName(cfg.getString("ijk"))
            mPlayerIJKBtn?.visibility = if (playerType == 1) VISIBLE else GONE
            mPlayerScaleBtn?.text = PlayerHelper.getScaleName(cfg.getInt("sc"))
            mPlayerSpeedBtn?.text = cfg.getDouble("sp").toString() + "x"
            val start = cfg.getInt("st")
            val end = cfg.getInt("et")
            mPlayerTimeStartBtn?.text = if (start == 0) "片头" else stringForTime(start * 1000)
            mPlayerTimeSkipBtn?.text = if (end == 0) "片尾" else stringForTime(end * 1000)
            mAudioTrackBtn?.visibility = if (playerType == 1 || playerType == 2) VISIBLE else GONE
            mVideoTrackBtn?.visibility = if (playerType == 1 || playerType == 2) VISIBLE else GONE
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun getPlayerShortName(playerType: Int): String {
        return PlayerHelper.getPlayerName(playerType)
    }

    private fun getCodecShortName(codecName: String?): String {
        if ("硬解码" == codecName) return "硬解"
        if ("软解码" == codecName) return "软解"
        return codecName ?: ""
    }

    fun setTitle(playTitleInfo: String?) {
        mPlayTitle?.text = playTitleInfo ?: ""
        mPlayTitle1?.text = playTitleInfo ?: ""
    }

    fun setUrlTitle(playTitleInfo: String?) {
        mPlayTitle?.text = playTitleInfo ?: ""
    }

    fun resetSpeed() {
        skipEnd = true
        mHandler.removeMessages(1004)
        mHandler.sendEmptyMessageDelayed(1004, 100)
    }

    fun setHasDanmu(hasDanmu: Boolean) {
        this.hasDanmu = hasDanmu
        updateDanmuBtn()
    }

    fun updateDanmuBtn() {
        if (mDanmuSettingBtn == null) return
        mDanmuSettingBtn?.visibility = if (DanmuHelper.isOpen()) VISIBLE else GONE
        updatePlayLabelVisibility()
    }

    fun updateDanmuSearchUiBtn() {
        if (mDanmuSearchUiBtn == null) return
        val hasDanmuSearchUi = ApiConfig.get().hasDanmuSearchUi()
        mDanmuSearchUiBtn?.visibility = if (hasDanmuSearchUi) VISIBLE else GONE
        updatePlayLabelVisibility()
    }

    private fun updatePlayLabelVisibility() {
        val playBtnGroup = mPlayBtnGroup ?: return
        var isDanmuMenuVisible = false
        var isScreenDisplayNext = false
        for (i in 0 until playBtnGroup.childCount) {
            val child = playBtnGroup.getChildAt(i)
            if (child.visibility != VISIBLE) continue
            if (child == mDanmuSettingBtn) {
                isDanmuMenuVisible = true
                continue
            }
            if (isDanmuMenuVisible) {
                isScreenDisplayNext = child == mScreenDisplay
                break
            }
        }
        mPlayLabel?.visibility = if (isDanmuMenuVisible && !isScreenDisplayNext) GONE else VISIBLE
    }

    interface VodControlListener {
        fun playNext(rmProgress: Boolean)
        fun playPre()
        fun showEpisodeDialog()
        fun prepared()
        fun changeParse(pb: ParseBean)
        fun updatePlayerCfg()
        fun replay(replay: Boolean)
        fun errReplay()
        fun selectSubtitle()
        fun selectAudioTrack()
        fun selectVideoTrack()
        fun showDanmuSetting()
        fun toggleDanmu(): Boolean
        fun searchDanmuUi(longClick: Boolean)
        fun startPlayUrl(url: String, headers: HashMap<String, String>?)
        fun onM3u8ProxyUrl(proxyUrl: String, sourceUrl: String)
        fun clickCast()
        fun setAllowSwitchPlayer(isAllow: Boolean)
    }

    fun setListener(listener: VodControlListener?) {
        this.listener = listener
    }

    private fun keepSeekBarActive() {
        if (myHandle != null && myRunnable != null) {
            myHandle?.removeCallbacks(myRunnable!!)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
        }
        mHandler.removeMessages(1002)
        mHandler.removeMessages(1003)
        if (mBottomRoot != null && mBottomRoot?.visibility != VISIBLE) {
            mHandler.sendEmptyMessage(1002)
        }
    }

    private fun beginSeekBarTracking() {
        keepSeekBarActive()
        if (mIsDragging) return
        mIsDragging = true
        mControlWrapper.stopProgress()
        mControlWrapper.stopFadeOut()
    }

    private fun finishSeekBarTracking() {
        keepSeekBarActive()
        val newPosition = getSeekBarPosition(mSeekBar?.progress ?: 0)
        mControlWrapper.seekTo(newPosition)
        mIsDragging = false
        mSeekBarKeyTracking = false
        mControlWrapper.startProgress()
        mControlWrapper.startFadeOut()
    }

    private fun getSeekBarPosition(progress: Int): Long {
        val duration = safeTimeMs(mControlWrapper.getDuration()).toLong()
        val max = mSeekBar?.max ?: 0
        if (duration <= 0 || max <= 0) {
            return 0
        }
        return (duration * progress) / max
    }

    private fun updateSeekBarTime(progress: Int) {
        val newPosition = getSeekBarPosition(progress)
        mCurrentTime?.text = stringForTime(safeTimeMs(newPosition))
    }

    private fun moveSeekBarByKey(dir: Int) {
        val seekBar = mSeekBar ?: return
        val duration = safeTimeMs(mControlWrapper.getDuration())
        if (duration <= 0 || seekBar.max <= 0) return
        var progress = seekBar.progress + getSeekBarKeyProgress(duration) * dir
        if (progress < 0) {
            progress = 0
        } else if (progress > seekBar.max) {
            progress = seekBar.max
        }
        seekBar.progress = progress
        updateSeekBarTime(progress)
        updateSeekUI(safeTimeMs(mControlWrapper.getCurrentPosition()), safeTimeMs(getSeekBarPosition(progress)), duration)
    }

    private fun getSeekBarKeyProgress(duration: Int): Int {
        val increment: Long = if (duration > 3 * 60 * 60 * 1000) {
            5 * 60 * 1000L
        } else if (duration > 30 * 60 * 1000) {
            60 * 1000L
        } else if (duration > 15 * 60 * 1000) {
            30 * 1000L
        } else if (duration > 10 * 60 * 1000) {
            15 * 1000L
        } else {
            10 * 1000L
        }
        val max = mSeekBar?.max ?: 0
        return Math.max(1, (increment * max / duration).toInt())
    }

    @SuppressLint("SetTextI18n")
    override fun setProgress(duration: Int, position: Int) {
        if (mIsDragging) return
        super.setProgress(duration, position)
        if (skipEnd && position != 0 && duration != 0) {
            var et = 0
            try {
                et = mPlayerConfig?.getInt("et") ?: 0
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false
                listener?.playNext(true)
            }
        }
        mCurrentTime?.text = stringForTime(position)
        mTotalTime?.text = stringForTime(duration)
        seekTime?.text = formatSeekTime(position) + " | " + formatSeekTime(duration)
        if (duration > 0) {
            mSeekBar?.isEnabled = true
            val pos = (position * 1.0 / duration * (mSeekBar?.max ?: 0)).toInt()
            mSeekBar?.progress = pos
        } else {
            mSeekBar?.isEnabled = false
        }
        val percent = mControlWrapper.getBufferedPercentage()
        if (percent >= 95) {
            mSeekBar?.secondaryProgress = mSeekBar?.max ?: 0
        } else {
            mSeekBar?.secondaryProgress = percent * 10
        }
    }

    private fun formatSeekTime(timeMs: Int): String {
        val totalSeconds = Math.max(0, timeMs) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun tvSlideStop() {
        if (!simSlideStart) return
        mControlWrapper.seekTo(simSeekPosition.toLong())
        if (!mControlWrapper.isPlaying()) {
            mControlWrapper.start()
        }
        simSlideStart = false
        simSeekPosition = 0
        simSlideOffset = 0
    }

    fun tvSlideStart(dir: Int) {
        val duration = safeTimeMs(mControlWrapper.getDuration())
        if (duration <= 0) return

        val currentTime = System.currentTimeMillis()
        val baseSkip = 10000
        val accelerationFactor = 2.0f
        val threshold = 800L

        if (!simSlideStart) {
            simSlideStart = true
            simSlideOffset = (baseSkip * dir).toLong()
        } else {
            if (currentTime - lastSlideTime <= threshold) {
                simSlideOffset += (baseSkip * accelerationFactor * dir).toLong()
            } else {
                simSlideOffset = (baseSkip * dir).toLong()
            }
        }
        lastSlideTime = currentTime
        val currentPosition = safeTimeMs(mControlWrapper.getCurrentPosition())
        var position = (currentPosition + simSlideOffset).toInt()
        if (position > duration) position = duration
        if (position < 0) position = 0
        updateSeekUI(currentPosition, position, duration)
        simSeekPosition = position
    }

    override fun updateSeekUI(curr: Int, seekTo: Int, duration: Int) {
        super.updateSeekUI(curr, seekTo, duration)
        if (seekTo > curr) {
            mProgressIcon?.setImageResource(R.drawable.exo_icon_fastforward)
        } else {
            mProgressIcon?.setImageResource(R.drawable.exo_icon_rewind)
        }
        mProgressText?.text = stringForTime(seekTo) + " / " + stringForTime(duration)
        mHandler.sendEmptyMessage(1000)
        mHandler.removeMessages(1001)
        mHandler.sendEmptyMessageDelayed(1001, 1000)
    }

    override fun onPlayStateChanged(playState: Int) {
        super.onPlayStateChanged(playState)
        videoPlayState = playState
        when (playState) {
            VideoView.STATE_IDLE -> {}
            VideoView.STATE_PLAYING -> {
                initLandscapePortraitBtnInfo()
                startProgress()
            }
            VideoView.STATE_PAUSED -> {
                mTopRoot1?.visibility = GONE
                mPlayLoadNetSpeedRightTop?.visibility = GONE
                if (isBottomVisible()) {
                    hideBottom()
                }
                showPauseRoot()
                mPlayTitle?.visibility = VISIBLE
            }
            VideoView.STATE_ERROR -> {
                listener?.errReplay()
            }
            VideoView.STATE_PREPARED -> {
                mPlayLoadNetSpeed?.visibility = GONE
                hideLiveAboutBtn()
                listener?.prepared()
            }
            VideoView.STATE_BUFFERED,
            VideoView.STATE_PREPARING,
            VideoView.STATE_BUFFERING -> {
                mPlayLoadNetSpeed?.visibility = GONE
            }
            VideoView.STATE_PLAYBACK_COMPLETED -> {
                listener?.playNext(true)
            }
        }
    }

    fun isBottomVisible(): Boolean {
        return mBottomRoot?.visibility == VISIBLE
    }

    fun showBottom() {
        mHandler.removeMessages(1003)
        mHandler.sendEmptyMessage(1002)
        mNextBtn?.requestFocus()
    }

    fun showUpBottom() {
        mHandler.removeMessages(1003)
        mHandler.sendEmptyMessage(1002)
        mPlayerTimeStartBtn?.requestFocus()
    }

    fun hideBottom() {
        mHandler.removeMessages(1002)
        mHandler.sendEmptyMessage(1003)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        myHandle?.removeCallbacks(myRunnable!!)
        if (super.onKeyEvent(event)) {
            return true
        }
        val keyCode = event.keyCode
        val action = event.action
        if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && keyCode == KeyEvent.KEYCODE_MENU) {
            listener?.showEpisodeDialog()
            hideBottom()
            return true
        }
        if (isBottomVisible()) {
            mHandler.removeMessages(1002)
            mHandler.removeMessages(1003)
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            return super.dispatchKeyEvent(event)
        }
        if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
            && videoPlayState == VideoView.STATE_ERROR
            && (keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        ) {
            showBottom()
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
            return true
        }
        val isInPlayback = isInPlaybackState()
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStart(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                    return true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback) {
                    if (event.repeatCount == 0) togglePlay()
                    return true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!isBottomVisible()) {
                    showBottom()
                    myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
                    return true
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStop()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun speedPlayStart() {
        fromLongPress = true
        try {
            speed_old = (mPlayerConfig?.getDouble("sp") ?: 1.0).toFloat()
            val speed = 3.0f
            mPlayerConfig?.put("sp", speed.toDouble())
            updatePlayerCfgView()
            listener?.updatePlayerCfg()
            mControlWrapper.setSpeed(speed)
            findViewById<View>(R.id.play_speed_3_container)?.visibility = View.VISIBLE
        } catch (f: JSONException) {
            f.printStackTrace()
        }
    }

    private fun speedPlayEnd() {
        if (fromLongPress) {
            fromLongPress = false
            try {
                val speed = speed_old
                mPlayerConfig?.put("sp", speed.toDouble())
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                mControlWrapper.setSpeed(speed)
            } catch (f: JSONException) {
                f.printStackTrace()
            }
            findViewById<View>(R.id.play_speed_3_container)?.visibility = View.GONE
        }
    }

    override fun onLongPress(e: MotionEvent) {
        if (videoPlayState != VideoView.STATE_PAUSED) {
            speedPlayStart()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            speedPlayEnd()
        }
        return super.onTouchEvent(e)
    }

    private fun setOnTvLongClickListener(view: View?, clickListener: View.OnLongClickListener) {
        if (view == null) return
        view.setOnLongClickListener(clickListener)
        view.setOnKeyListener(object : View.OnKeyListener {
            private var longPressTriggered = false
            private val longPressRunnable = Runnable {
                if (view.hasFocus() && view.isShown) {
                    longPressTriggered = true
                    view.performLongClick()
                }
            }

            private fun isConfirmKey(keyCode: Int): Boolean {
                return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A
            }

            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                if (!isConfirmKey(keyCode)) return false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        longPressTriggered = false
                        view.removeCallbacks(longPressRunnable)
                        view.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    return true
                }
                if (event.action == KeyEvent.ACTION_UP) {
                    view.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered) {
                        view.performClick()
                    }
                    return true
                }
                return true
            }
        })
    }

    private fun setMinPlayTimeChange(typeEt: String, increase: Boolean): Boolean {
        myHandle?.removeCallbacks(myRunnable!!)
        myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
        try {
            val currentValue = mPlayerConfig?.optInt(typeEt, 0) ?: 0
            if (currentValue != 0) {
                var newValue = if (increase) currentValue + 1 else currentValue - 1
                if (newValue < 0) {
                    newValue = 0
                }
                mPlayerConfig?.put(typeEt, newValue)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
                return true
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isBottomVisible()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (mPlayerTimeStartBtn?.hasFocus() == true) {
                    if (setMinPlayTimeChange("st", true)) {
                        return true
                    }
                }
                val focusedView = mPlayBtnGroup?.findFocus()
                if (focusedView is TextView) {
                    return true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (mPlayerTimeStartBtn?.hasFocus() == true) {
                    if (setMinPlayTimeChange("st", false)) return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.repeatCount == 0) {
            isLongPressTriggered = false
            mLongPressRunnable = Runnable {
                speedPlayStart()
                isLongPressTriggered = true
            }
            mmHandler.postDelayed(mLongPressRunnable!!, LONG_PRESS_DELAY)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (mLongPressRunnable != null) {
                mmHandler.removeCallbacks(mLongPressRunnable!!)
                mLongPressRunnable = null
            }
            if (isLongPressTriggered) {
                speedPlayEnd()
            } else {
                if (!isBottomVisible()) {
                    showUpBottom()
                    myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
                } else {
                    return super.onKeyUp(keyCode, event)
                }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        myHandle?.removeCallbacks(myRunnable!!)
        if (!isBottomVisible()) {
            showBottom()
            myHandle?.postDelayed(myRunnable!!, myHandleSeconds.toLong())
        } else {
            hideBottom()
        }
        return true
    }

    inner class LockRunnable : Runnable {
        override fun run() {
            mLockView?.visibility = INVISIBLE
        }
    }

    override fun onBackPressed(): Boolean {
        if (isClickBackBtn) {
            isClickBackBtn = false
            if (isBottomVisible()) {
                hideBottom()
            }
            return false
        }
        if (super.onBackPressed()) {
            return true
        }
        if (isBottomVisible()) {
            hideBottom()
            return true
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler.removeCallbacks(myRunnable2)
    }

    fun getWebPlayUrlIfNeeded(webPlayUrl: String?): String? {
        if (webPlayUrl != null && !webPlayUrl.contains("127.0.0.1:9978") && webPlayUrl.contains(".m3u8")) {
            try {
                val urlEncode = URLEncoder.encode(webPlayUrl, "UTF-8")
                LOG.i("echo-BOM-------")
                return ControlManager.get().getAddress(true) + "proxy?go=bom&url=" + urlEncode
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
        return webPlayUrl
    }

    fun encodeUrl(url: String?): String? {
        return try {
            if (url == null) null else URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
    }

    fun switchPlayer(): Boolean {
        try {
            val playerType = mPlayerConfig?.getInt("pl") ?: 0
            val pType = if (playerType == 1) playerType + 1 else if (playerType == 2) playerType - 1 else playerType
            if (pType != playerType) {
                LOG.i("echo-switchPlayer: $playerType -> $pType")
                mPlayerConfig?.put("pl", pType)
                updatePlayerCfgView()
                listener?.updatePlayerCfg()
            } else {
                LOG.i("echo-switchPlayer: skip unsupported playerType=$playerType")
                return true
            }
        } catch (e: Exception) {
            LOG.i("echo-switchPlayer error: " + e.message)
            return true
        }
        return false
    }

    fun playM3u8(url: String, headers: HashMap<String, String>?) {
        if (url.contains("url=")) {
            listener?.startPlayUrl(url, headers)
            return
        }
        OkGo.getInstance().cancelTag("m3u8-1")
        OkGo.getInstance().cancelTag("m3u8-2")
        val okGoHeaders = HttpHeaders()
        if (headers != null) {
            for ((key, value) in headers) {
                okGoHeaders.put(key, value)
            }
        }
        OkGo.get<String>(url)
            .tag("m3u8-1")
            .headers(okGoHeaders)
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>) {
                    val content = response.body() ?: ""
                    if (!content.startsWith("#EXTM3U")) {
                        listener?.startPlayUrl(url, headers)
                        return
                    }
                    val forwardUrl = extractForwardUrl(url, content)
                    if (forwardUrl.isEmpty()) {
                        LOG.i("echo-m3u81-to-play")
                        processM3u8Content(url, content, headers)
                    } else {
                        fetchAndProcessForwardUrl(forwardUrl, headers, okGoHeaders, url)
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()?.string() ?: ""
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    LOG.e("echo-m3u8请求错误1: " + response.exception)
                    listener?.startPlayUrl(url, headers)
                }
            })
    }

    private fun extractForwardUrl(baseUrl: String, content: String): String {
        val lines = content.split("\r?\n".toRegex(), limit = 50).toTypedArray()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                for (j in i + 1 until lines.size) {
                    val targetLine = lines[j].trim()
                    if (targetLine.isEmpty()) continue
                    if (isValidM3u8Line(targetLine)) {
                        return resolveForwardUrl(baseUrl, targetLine)
                    }
                }
            }
        }
        return ""
    }

    private fun isValidM3u8Line(line: String): Boolean {
        return !line.startsWith("#") && (line.endsWith(".m3u8") || line.contains(".m3u8?"))
    }

    private fun processM3u8Content(url: String, content: String, headers: HashMap<String, String>?) {
        val basePath = getBasePath(url)
        RemoteServer.m3u8Content = M3u8.purify(basePath, content)
        if (RemoteServer.m3u8Content == null || M3u8.currentAdCount == 0) {
            LOG.i("echo-m3u8内容解析：未检测到广告")
            listener?.startPlayUrl(url, headers)
        } else {
            val proxyUrl = ControlManager.get().getAddress(true) + "proxyM3u8"
            listener?.onM3u8ProxyUrl(proxyUrl, url)
            listener?.startPlayUrl(proxyUrl, headers)
            Toast.makeText(context, "已移除视频广告 " + M3u8.currentAdCount + " 条", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAndProcessForwardUrl(
        forwardUrl: String,
        headers: HashMap<String, String>?,
        okGoHeaders: HttpHeaders,
        fallbackUrl: String
    ) {
        OkGo.get<String>(forwardUrl)
            .tag("m3u8-2")
            .headers(okGoHeaders)
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>) {
                    val content = response.body() ?: ""
                    LOG.i("echo-m3u82-to-play")
                    processM3u8Content(forwardUrl, content, headers)
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()?.string() ?: ""
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    LOG.e("echo-重定向 m3u8 请求错误: " + response.exception)
                    listener?.startPlayUrl(fallbackUrl, headers)
                }
            })
    }

    private fun getBasePath(url: String): String {
        val ilast = url.lastIndexOf('/')
        return url.substring(0, ilast + 1)
    }

    private fun resolveForwardUrl(baseUrl: String, line: String): String {
        return try {
            val base = URL(baseUrl)
            val resolved = URL(base, line)
            resolved.toString()
        } catch (e: MalformedURLException) {
            LOG.e("echo-resolveForwardUrl异常: " + e.message)
            line
        }
    }

    fun firstUrlByArray(url: String): String {
        var result = url
        try {
            val urlArray = JSONArray(url)
            for (i in 0 until urlArray.length()) {
                val item = urlArray.getString(i)
                if (item.contains("http")) {
                    result = item
                    break
                }
            }
        } catch (ignored: JSONException) {
        }
        return result
    }

    fun evaluateScript(sourceBean: SourceBean, url: String, web_view: WebView?, xWalk_view: XWalkView?) {
        var clickSelector = sourceBean.clickSelector.trim()
        clickSelector = if (clickSelector.isEmpty()) VideoParseRuler.getHostScript(url) else clickSelector
        if (clickSelector.isNotEmpty()) {
            val selector: String = if (clickSelector.contains(";") && !clickSelector.endsWith(";")) {
                val parts = clickSelector.split(";".toRegex(), limit = 2).toTypedArray()
                if (!url.contains(parts[0])) {
                    return
                }
                parts[1].trim()
            } else {
                clickSelector.trim()
            }
            val js = selector
            LOG.i("echo-javascript:$js")
            if (web_view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    web_view.evaluateJavascript(js, null)
                } else {
                    web_view.loadUrl("javascript:$js")
                }
            }
            xWalk_view?.evaluateJavascript(js, null)
        }
    }

    fun stopOther() {
        Thunder.stop(false)
        Jianpian.finish()
        App.getInstance().setDashData(null)
    }

    private class CircleThumbDrawable(context: Context) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val normalSize: Int
        private val activeSize: Int
        private val strokeWidth: Float
        private var active = false

        init {
            normalSize = dp(context, 12f)
            activeSize = dp(context, 16f)
            strokeWidth = dp(context, 2f).toFloat()
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = Color.WHITE
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = Color.parseColor("#FF4081")
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val size = if (active) activeSize else normalSize
            val radius = Math.max(0f, size / 2f - strokeWidth / 2f)
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }

        override fun isStateful(): Boolean {
            return true
        }

        override fun onStateChange(stateSet: IntArray): Boolean {
            var newActive = false
            if (stateSet != null) {
                for (state in stateSet) {
                    if (state == android.R.attr.state_pressed
                        || state == android.R.attr.state_focused
                        || state == android.R.attr.state_selected
                    ) {
                        newActive = true
                        break
                    }
                }
            }
            if (active == newActive) {
                return false
            }
            active = newActive
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }

        override fun getIntrinsicWidth(): Int {
            return activeSize
        }

        override fun getIntrinsicHeight(): Int {
            return activeSize
        }

        companion object {
            private fun dp(context: Context, value: Float): Int {
                return (value * context.resources.displayMetrics.density + 0.5f).toInt()
            }
        }
    }
}
