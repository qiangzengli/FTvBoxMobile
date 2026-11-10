package com.github.tvbox.osc.ui.fragment

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.DanmakuApi
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.ParseBean
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.Subtitle
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.dlna.CastVideo
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.ExoPlayer
import com.github.tvbox.osc.player.IjkMediaPlayer
import com.github.tvbox.osc.player.MusicPlaybackService
import com.github.tvbox.osc.player.MyVideoView
import com.github.tvbox.osc.player.TrackInfo
import com.github.tvbox.osc.player.TrackInfoBean
import com.github.tvbox.osc.player.controller.VodController
import com.github.tvbox.osc.player.danmu.DanmuLoadController
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.dialog.CastDeviceDialog
import com.github.tvbox.osc.ui.dialog.DanmuSettingDialog
import com.github.tvbox.osc.ui.dialog.EpisodeDialog
import com.github.tvbox.osc.ui.dialog.SearchDanmuDialog
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.SubtitleDialog
import com.github.tvbox.osc.util.AdBlocker
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.PlayerHelper
import com.github.tvbox.osc.util.SubtitleHelper
import com.github.tvbox.osc.util.VideoParseRuler
import com.github.tvbox.osc.util.XWalkUtils
import com.github.tvbox.osc.util.parser.SuperParse
import com.github.tvbox.osc.util.thunder.Jianpian
import com.github.tvbox.osc.util.thunder.Thunder
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.android.exoplayer2.text.Cue
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.HttpHeaders
import com.lzy.okgo.model.Response
import com.obsez.android.lib.filechooser.ChooserDialog
import com.orhanobut.hawk.Hawk
import master.flame.danmaku.ui.widget.DanmakuView
import me.jessyan.autosize.AutoSize
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xwalk.core.XWalkJavascriptResult
import org.xwalk.core.XWalkResourceClient
import org.xwalk.core.XWalkSettings
import org.xwalk.core.XWalkUIClient
import org.xwalk.core.XWalkView
import org.xwalk.core.XWalkWebResourceRequest
import org.xwalk.core.XWalkWebResourceResponse
import tv.danmaku.ijk.media.player.IMediaPlayer
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.ProgressManager
import xyz.doikki.videoplayer.player.VideoView
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

open class PlayFragment : BaseLazyFragment() {
    companion object {
        private const val MSG_PARSE_TIMEOUT = 100
        private const val MSG_RESOLVE_PLAY_URL_TIMEOUT = 101
        private const val MSG_SWITCH_LINE_PLAY_TIMEOUT = 102
        private const val RESOLVE_PLAY_URL_TIMEOUT_MS = 15 * 1000L
        private const val SWITCH_LINE_PLAY_TIMEOUT_MS = 20 * 1000L
    }

    private var mVideoView: MyVideoView? = null
    private var mPlayLoadTip: TextView? = null
    private var mPlayLoadErr: ImageView? = null
    private var mPlayLoading: ProgressBar? = null
    private var mController: VodController? = null
    private var sourceViewModel: SourceViewModel? = null
    private var playResultObserver: Observer<JSONObject>? = null
    private var qualityResult: JSONObject? = null
    private var mHandler: Handler? = null
    private var exitingPreview = false
    private var audioPlayback = false
    private var switchingPlayback = false
    private var reusePlayerOnSwitch = false
    private var releasePlayerOnSwitch = false
    private var previewMode = false
    private var playLyric: String? = null
    private var lyricCacheKey: String? = null
    private var playArtwork: String? = null
    private var mDanmuView: DanmakuView? = null
    private var danmuLoadController: DanmuLoadController? = null
    private val exoCues = ArrayList<Cue>()
    private var exoInternalSubtitle = false

    private var mVodInfo: VodInfo? = null
    private var mVodPlayerCfg: JSONObject? = null
    private var sourceKey: String? = null
    private var sourceBean: SourceBean? = null

    private var autoRetryCount = 0
    private var lastRetryTime: Long = 0

    private var allowSwitchPlayer = true
    private var hasAutoSwitchedPlayer = false
    private var autoSwitchedPlayerType = -1
    private var allowAutoSwitchLine = true
    private var playbackStarted = false
    private var playTimeoutBasePosition: Long = 0
    private val triedLineFlags = HashSet<String>()

    private var playSubtitle: String? = null
    private var subtitleCacheKey: String? = null
    private var progressKey: String? = null
    private var inheritProgressKey: String? = null
    private var inheritProgress: Long = 0
    private var parseFlag: String? = null
    private var webUrl: String? = null
    private var webUserAgent: String? = null
    private var webHeaderMap: HashMap<String, String>? = null
    private var webPlayUrl: String? = null
    private var m3u8ProxyUrl: String? = null
    private var m3u8SourceUrl: String? = null

    private var parseThreadPool: ExecutorService? = null
    private var mXwalkWebView: XWalkView? = null
    private var mSysWebView: WebView? = null
    private val loadedUrls = HashMap<String, Boolean>()
    private var loadFoundVideoUrls = LinkedList<String>()
    private var loadFoundVideoUrlsHeader = HashMap<String, HashMap<String, String>>()
    private val loadFoundCount = AtomicInteger(0)

    override fun getLayoutResID(): Int {
        return R.layout.activity_play
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            val size = (event.obj as? Number)?.toInt() ?: 20
            mController?.mSubtitleView?.setTextSize(size.toFloat())
        }
        if (event.type == RefreshEvent.TYPE_SET_DANMU_SETTINGS) {
            setDanmuViewSettings(event.obj is Boolean && event.obj as Boolean)
        } else if (event.type == RefreshEvent.TYPE_DANMU_REFRESH) {
            checkDanmu(if (event.obj is String) event.obj as String else "")
        }
    }

    override fun init() {
        initView()
        initDanmuView()
        initViewModel()
        initData()
    }

    private fun initDanmuView() {
        mDanmuView = findViewById(R.id.danmaku)
        val video = mVideoView ?: return
        val ctrl = mController ?: return
        val danmu = mDanmuView ?: return
        danmuLoadController = DanmuLoadController(video, ctrl, danmu)
    }

    private fun setDanmuViewSettings(reload: Boolean) {
        danmuLoadController?.applySettings(reload)
    }

    private fun checkDanmu(danmu: String) {
        checkDanmu(danmu, null)
    }

    private fun checkDanmu(danmu: String, callback: DanmuLoadController.LoadCallback?) {
        val info = mVodInfo
        val series = if (info == null) null else getCurrentSeries(info.playFlag, info.playIndex)
        val vodName = info?.name ?: ""
        val seriesName = series?.name ?: ""
        danmuLoadController?.check(danmu, vodName, seriesName, callback)
    }

    private fun startDanmuIfReady() {
        danmuLoadController?.startIfReady()
    }

    private fun resetDanmuState() {
        danmuLoadController?.reset()
    }

    private fun reloadDanmuForPlayback() {
        danmuLoadController?.reloadForPlayback()
    }

    fun getSavedProgress(url: String): Long {
        var st = 0
        try {
            st = mVodPlayerCfg?.optInt("st", 0) ?: 0
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val skip = st * 1000L
        val theCache = CacheManager.getCache(MD5.string2MD5(url)) ?: return skip
        var rec: Long = 0
        when (theCache) {
            is Long -> rec = theCache
            is Number -> rec = theCache.toLong()
            is String -> {
                try {
                    rec = theCache.toLong()
                } catch (e: NumberFormatException) {
                    LOG.i("echo-String value is not a valid long.")
                }
            }
            else -> LOG.i("echo-Value cannot be converted to long.")
        }
        return maxOf(rec, skip)
    }

    private fun initView() {
        EventBus.getDefault().register(this)
        mHandler = Handler(Looper.getMainLooper(), Handler.Callback { msg ->
            when (msg.what) {
                MSG_PARSE_TIMEOUT -> {
                    stopParse()
                    errorWithRetry("嗅探错误", false)
                }
                MSG_RESOLVE_PLAY_URL_TIMEOUT -> handleResolvePlayUrlTimeout()
                MSG_SWITCH_LINE_PLAY_TIMEOUT -> handleSwitchLinePlayTimeout()
            }
            false
        })
        mVideoView = findViewById(R.id.mVideoView)
        mPlayLoadTip = findViewById(R.id.play_load_tip)
        mPlayLoading = findViewById(R.id.play_loading)
        mPlayLoadErr = findViewById(R.id.play_load_error)
        val ctrl = VodController(requireContext())
        mController = ctrl

        val passThroughTouch = View.OnTouchListener { _, event ->
            mController?.onTouchEvent(event) ?: false
        }
        mPlayLoadTip?.setOnTouchListener(passThroughTouch)
        mPlayLoadErr?.setOnTouchListener(passThroughTouch)
        ctrl.mLyricView?.setTextSize(if (previewMode) 16f else 24f)
        ctrl.setCanChangePosition(true)
        ctrl.setEnableInNormal(true)
        ctrl.setGestureEnabled(true)

        val progressManager = object : ProgressManager() {
            override fun saveProgress(url: String, progress: Long) {
                CacheManager.save(MD5.string2MD5(url), progress)
                if (webPlayUrl != null && progress > 0) {
                    markPlaybackStarted()
                    hideTipOnUiThread()
                }
            }

            override fun getSavedProgress(url: String): Long {
                return this@PlayFragment.getSavedProgress(url)
            }
        }
        mVideoView?.setProgressManager(progressManager)
        mVideoView?.addOnStateChangeListener(object : VideoView.SimpleOnStateChangeListener() {
            override fun onPlayStateChanged(playState: Int) {
                if (webPlayUrl != null && isStartedPlayState(playState)) {
                    markPlaybackStarted()
                    hideTipOnUiThread()
                }
                if (switchingPlayback) {
                    if (playState == VideoView.STATE_PLAYBACK_COMPLETED) {
                        LOG.i("echo-music keep session while resolving next episode")
                        return
                    } else if (playState == VideoView.STATE_ERROR) {
                        switchingPlayback = false
                        audioPlayback = false
                    } else if (isStartedPlayState(playState)) {
                        val audioOnly = getAudioOnlyPlayback()
                        if (audioOnly != null) {
                            switchingPlayback = false
                            audioPlayback = audioOnly
                        }
                    }
                }
                if (!switchingPlayback) updateMusicSession()
                startDanmuIfReady()
            }
        })

        ctrl.setListener(object : VodController.VodControlListener {
            override fun showDanmuSetting() {
                val dialog = DanmuSettingDialog(requireContext())
                dialog.setDanmuSearchListener(object : DanmuSettingDialog.DanmuSearchListener {
                    override fun openSearchDanmuDialog() {
                        val searchDanmuDialog = SearchDanmuDialog(requireContext())
                        searchDanmuDialog.setDanmuLoader(object : SearchDanmuDialog.DanmuLoader {
                            override fun loadDanmu(danmu: String) {
                                if (!isAdded) return
                                checkDanmu(danmu)
                            }
                        })
                        val info = mVodInfo
                        val series = if (info == null) null else getCurrentSeries(info.playFlag, info.playIndex)
                        searchDanmuDialog.setEpisode(series?.name ?: "")
                        searchDanmuDialog.setSearchWord(info?.name ?: "")
                        searchDanmuDialog.show()
                    }
                })
                dialog.show()
            }

            override fun toggleDanmu(): Boolean {
                return danmuLoadController?.toggle() ?: false
            }

            override fun searchDanmuUi(longClick: Boolean) {
                val info = mVodInfo
                val series = if (info == null) null else getCurrentSeries(info.playFlag, info.playIndex)
                ApiConfig.get().searchDanmuUi(info?.name ?: "", series?.name ?: "", longClick)
            }

            override fun playNext(rmProgress: Boolean) {
                val preProgressKey = progressKey
                this@PlayFragment.playNext(rmProgress)
                if (rmProgress && preProgressKey != null) {
                    CacheManager.delete(MD5.string2MD5(preProgressKey), 0)
                }
            }

            override fun playPre() {
                this@PlayFragment.playPrevious()
            }

            override fun showEpisodeDialog() {
                this@PlayFragment.showEpisodeDialog()
            }

            override fun changeParse(pb: ParseBean) {
                autoRetryCount = 0
                hasAutoSwitchedPlayer = false
                triedLineFlags.clear()
                doParse(pb)
            }

            override fun updatePlayerCfg() {
                val info = mVodInfo ?: return
                info.playerCfg = mVodPlayerCfg.toString()
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg))
            }

            override fun replay(replay: Boolean) {
                autoRetryCount = 0
                hasAutoSwitchedPlayer = false
                triedLineFlags.clear()
                if (replay) {
                    play(true)
                } else {
                    reloadDanmuForPlayback()
                    val url = webPlayUrl
                    if (!url.isNullOrEmpty()) {
                        stopParse()
                        initParseLoadFound()
                        mVideoView?.release()
                        goPlayUrl(url, webHeaderMap)
                    } else {
                        play(false)
                    }
                }
            }

            override fun errReplay() {
                errorWithRetry("视频播放出错", false)
            }

            override fun selectSubtitle() {
                try {
                    selectMySubtitle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun selectAudioTrack() {
                selectMyAudioTrack()
            }

            override fun selectVideoTrack() {
                selectMyVideoTrack()
            }

            override fun prepared() {
                initSubtitleView()
                mVideoView?.prepared()
                startDanmuIfReady()
            }

            override fun startPlayUrl(url: String, headers: HashMap<String, String>?) {
                if (!TextUtils.isEmpty(m3u8SourceUrl) && !isM3u8ProxyUrl(url)) clearM3u8ProxyUrl()
                goPlayUrl(url, headers)
            }

            override fun onM3u8ProxyUrl(proxyUrl: String, sourceUrl: String) {
                m3u8ProxyUrl = proxyUrl
                m3u8SourceUrl = sourceUrl
            }

            override fun clickCast() {
                showCastDialog()
            }

            override fun setAllowSwitchPlayer(isAllow: Boolean) {
                allowSwitchPlayer = isAllow
            }
        })
        mVideoView?.setVideoController(ctrl)
    }

    private fun showCastDialog() {
        val playUrl = webPlayUrl
        if (TextUtils.isEmpty(playUrl)) {
            Toast.makeText(context ?: requireContext(), "暂无可投屏播放地址", Toast.LENGTH_SHORT).show()
            return
        }
        val headers = if (webHeaderMap == null) null else HashMap(webHeaderMap)
        val video = CastVideo(getCastUrl(playUrl ?: ""), getCastTitle(), headers, getCastPosition())
        val dialog = CastDeviceDialog(requireActivity(), video)
        dialog.setOnCastListener(object : CastDeviceDialog.OnCastListener {
            override fun onCastSuccess() {
                mVideoView?.pause()
            }

            override fun onCastFailed() {
            }
        })
        dialog.show()
    }

    private fun getCastTitle(): String {
        val info = mVodInfo ?: return "TVBox"
        return try {
            val series = info.seriesMap?.get(info.playFlag)?.get(info.playIndex)
            (info.name ?: "") + " " + (series?.name ?: "")
        } catch (e: Exception) {
            if (TextUtils.isEmpty(info.name)) "TVBox" else (info.name ?: "TVBox")
        }
    }

    private fun getCastPosition(): Long {
        return try {
            mVideoView?.getCurrentPosition() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun getCastUrl(url: String): String {
        if (TextUtils.isEmpty(url)) return url
        if (isM3u8ProxyUrl(url) && !TextUtils.isEmpty(m3u8SourceUrl)) return m3u8SourceUrl ?: url
        val local = ControlManager.get().getAddress(true)
        val server = ControlManager.get().getAddress(false)
        if (!TextUtils.isEmpty(local) && !TextUtils.isEmpty(server) && url.startsWith(local)) {
            return server + url.substring(local.length)
        }
        return url
    }

    private fun isM3u8ProxyUrl(url: String): Boolean {
        return !TextUtils.isEmpty(m3u8ProxyUrl) && url == m3u8ProxyUrl
    }

    private fun clearM3u8ProxyUrl() {
        m3u8ProxyUrl = null
        m3u8SourceUrl = null
    }

    // 设置字幕
    fun setSubtitle(path: String?) {
        if (!path.isNullOrEmpty()) {
            hideExoInternalSubtitle()
            mController?.mSubtitleView?.visibility = View.GONE
            mController?.mSubtitleView?.setSubtitlePath(path)
            mController?.mSubtitleView?.visibility = View.VISIBLE
        }
    }

    @Throws(Exception::class)
    fun selectMySubtitle() {
        val act = activity ?: return
        val subtitleDialog = SubtitleDialog(act)
        val mediaPlayer = mVideoView?.getMediaPlayer()
        val hasInternal = (mController?.mSubtitleView?.hasInternal == true) || hasExoInternalSubtitle(mediaPlayer)
        subtitleDialog.selectInternal?.visibility = if (hasInternal) View.VISIBLE else View.GONE
        subtitleDialog.setExoInternalSubtitle(mediaPlayer is ExoPlayer && exoInternalSubtitle)
        subtitleDialog.setSubtitleViewListener(object : SubtitleDialog.SubtitleViewListener {
            override fun setTextSize(size: Int) {
                mController?.mSubtitleView?.setTextSize(size.toFloat())
            }

            override fun setSubtitleDelay(milliseconds: Int) {
                if (mediaPlayer is ExoPlayer && exoInternalSubtitle) {
                    mediaPlayer.setInternalSubtitleDelay(SubtitleHelper.getTimeDelay())
                } else {
                    mController?.mSubtitleView?.setSubtitleDelay(milliseconds)
                }
            }

            override fun selectInternalSubtitle() {
                selectMyInternalSubtitle()
            }

            override fun setTextStyle(style: Int) {
                setSubtitleViewTextStyle(style)
            }

            override fun setSubtitleScale(scale: Int) {
                if (mediaPlayer is ExoPlayer && exoInternalSubtitle) {
                    applyExoSubtitleSettings()
                }
            }

            override fun moveSubtitle(offset: Float) {
                if (mediaPlayer is ExoPlayer && exoInternalSubtitle) {
                    applyExoSubtitleSettings()
                }
            }
        })
        subtitleDialog.setSearchSubtitleListener(object : SubtitleDialog.SearchSubtitleListener {
            override fun openSearchSubtitleDialog() {
                val searchSubtitleDialog = SearchSubtitleDialog(act)
                searchSubtitleDialog.setSubtitleLoader(object : SearchSubtitleDialog.SubtitleLoader {
                    override fun loadSubtitle(subtitle: Subtitle) {
                        if (!isAdded) return
                        requireActivity().runOnUiThread {
                            val zimuUrl = subtitle.getUrl()
                            LOG.i("echo-Remote Subtitle Url: $zimuUrl")
                            setSubtitle(zimuUrl)
                            searchSubtitleDialog.dismiss()
                        }
                    }
                })
                val info = mVodInfo
                val pFlag = info?.playFlag ?: ""
                if (pFlag.contains("Ali") || pFlag.contains("parse")) {
                    searchSubtitleDialog.setSearchWord(info?.playNote ?: "")
                } else if (info != null) {
                    searchSubtitleDialog.setSearchWord(info.name ?: "")
                }
                searchSubtitleDialog.show()
            }
        })
        subtitleDialog.setLocalFileChooserListener(object : SubtitleDialog.LocalFileChooserListener {
            override fun openLocalFileChooserDialog() {
                ChooserDialog(act)
                    .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                    .withStartFile("/storage/emulated/0/Download")
                    .withChosenListener { path, _ ->
                        LOG.i("echo-Local Subtitle Path: $path")
                        setSubtitle(path)
                    }
                    .build()
                    .show()
            }
        })
        subtitleDialog.show()
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    fun setSubtitleViewTextStyle(style: Int) {
        val ctx = context ?: return
        if (style == 0) {
            mController?.mSubtitleView?.setTextColor(ctx.resources.getColorStateList(R.color.color_FFFFFF))
        } else if (style == 1) {
            mController?.mSubtitleView?.setTextColor(ctx.resources.getColorStateList(R.color.color_FFB6C1))
        }
    }

    private fun isSameTrack(left: TrackInfoBean, right: TrackInfoBean): Boolean {
        return left.renderId == right.renderId &&
                left.trackGroupId == right.trackGroupId &&
                left.trackId == right.trackId
    }

    fun selectMyAudioTrack() {
        val mediaPlayer = mVideoView?.getMediaPlayer()
        var trackInfo: TrackInfo? = null
        if (mediaPlayer is IjkMediaPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        } else if (mediaPlayer is ExoPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        }
        if (trackInfo == null) {
            Toast.makeText(context ?: requireContext(), "没有音轨", Toast.LENGTH_SHORT).show()
            return
        }
        val bean = trackInfo.getAudio()
        if (bean.isEmpty()) return
        val act = activity ?: return
        val dialog = SelectDialog<TrackInfoBean>(act)
        dialog.setTip("切换音轨")
        dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<TrackInfoBean> {
            override fun click(value: TrackInfoBean, pos: Int) {
                try {
                    for (audio in bean) {
                        audio.selected = isSameTrack(audio, value)
                    }
                    mediaPlayer?.pause()
                    val progress = mediaPlayer?.getCurrentPosition() ?: 0L
                    if (mediaPlayer is IjkMediaPlayer) mediaPlayer.setTrack(value.trackId, progressKey ?: "")
                    if (mediaPlayer is ExoPlayer) mediaPlayer.setTrack(value, progressKey ?: "")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (mediaPlayer is IjkMediaPlayer) mediaPlayer.seekTo(progress)
                        mediaPlayer?.start()
                    }, 200)
                    dialog.dismiss()
                } catch (e: Exception) {
                    LOG.e("切换音轨出错")
                }
            }

            override fun getDisplay(valItem: TrackInfoBean): String {
                return valItem.name ?: ""
            }
        }, object : DiffUtil.ItemCallback<TrackInfoBean>() {
            override fun areItemsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }
        }, bean, trackInfo.getAudioSelected(false))
        dialog.show()
    }

    fun selectMyVideoTrack() {
        val mediaPlayer = mVideoView?.getMediaPlayer()
        var trackInfo: TrackInfo? = null
        if (mediaPlayer is IjkMediaPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        } else if (mediaPlayer is ExoPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        }
        if (trackInfo == null || trackInfo.getVideo().isEmpty()) {
            Toast.makeText(context ?: requireContext(), "没有视轨", Toast.LENGTH_SHORT).show()
            return
        }
        val tracks = trackInfo.getVideo()
        val act = activity ?: return
        val dialog = SelectDialog<TrackInfoBean>(act)
        dialog.setTip("切换视轨")
        dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<TrackInfoBean> {
            override fun click(value: TrackInfoBean, pos: Int) {
                try {
                    for (track in tracks) {
                        track.selected = isSameTrack(track, value)
                    }
                    mediaPlayer?.pause()
                    val progress = mediaPlayer?.getCurrentPosition() ?: 0L
                    if (mediaPlayer is IjkMediaPlayer) {
                        mediaPlayer.setTrack(value.trackId)
                    } else if (mediaPlayer is ExoPlayer) {
                        mediaPlayer.setTrack(value, "")
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        mediaPlayer?.seekTo(progress)
                        mediaPlayer?.start()
                    }, 200)
                    dialog.dismiss()
                } catch (e: Exception) {
                    LOG.e("echo-switch-video-track-error:" + e.message)
                }
            }

            override fun getDisplay(valItem: TrackInfoBean): String {
                return valItem.name ?: ""
            }
        }, object : DiffUtil.ItemCallback<TrackInfoBean>() {
            override fun areItemsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }
        }, tracks, trackInfo.getVideoSelected(false))
        dialog.show()
    }

    fun selectMyInternalSubtitle() {
        val mediaPlayer = mVideoView?.getMediaPlayer()
        var trackInfo: TrackInfo? = null
        if (mediaPlayer is IjkMediaPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        } else if (mediaPlayer is ExoPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
        }
        if (trackInfo == null) {
            Toast.makeText(context ?: requireContext(), "没有内置字幕", Toast.LENGTH_SHORT).show()
            return
        }
        val bean = trackInfo.getSubtitle()
        if (bean.isEmpty()) return
        val act = activity ?: return
        val dialog = SelectDialog<TrackInfoBean>(act)
        dialog.setTip("切换内置字幕")
        dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<TrackInfoBean> {
            override fun click(value: TrackInfoBean, pos: Int) {
                try {
                    for (subtitle in bean) {
                        subtitle.selected = isSameTrack(subtitle, value)
                    }
                    if (mediaPlayer is IjkMediaPlayer) {
                        mediaPlayer.pause()
                        val progress = mediaPlayer.getCurrentPosition()
                        val subView = mController?.mSubtitleView
                        subView?.destroy()
                        subView?.clearSubtitleCache()
                        subView?.isInternal = true
                        mediaPlayer.setTrack(value.trackId)
                        Handler(Looper.getMainLooper()).postDelayed({
                            mediaPlayer.seekTo(progress)
                            mediaPlayer.start()
                        }, 800)
                    } else if (mediaPlayer is ExoPlayer) {
                        val subView = mController?.mSubtitleView
                        subView?.visibility = View.GONE
                        subView?.destroy()
                        subView?.clearSubtitleCache()
                        subView?.isInternal = false
                        exoInternalSubtitle = true
                        mediaPlayer.setTrack(value, "")
                        mediaPlayer.setInternalSubtitleDelay(SubtitleHelper.getTimeDelay())
                        mController?.mExoSubtitleView?.visibility = View.VISIBLE
                        applyExoSubtitleSettings()
                    }
                    dialog.dismiss()
                } catch (e: Exception) {
                    LOG.e("echo-switch-internal-subtitle-error:" + e.message)
                }
            }

            override fun getDisplay(valItem: TrackInfoBean): String {
                return valItem.name ?: ""
            }
        }, object : DiffUtil.ItemCallback<TrackInfoBean>() {
            override fun areItemsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItem: TrackInfoBean, newItem: TrackInfoBean): Boolean {
                return isSameTrack(oldItem, newItem)
            }
        }, bean, trackInfo.getSubtitleSelected(false))
        dialog.show()
    }

    private fun hasExoInternalSubtitle(mediaPlayer: AbstractPlayer?): Boolean {
        if (mediaPlayer !is ExoPlayer) return false
        val trackInfo = mediaPlayer.getTrackInfo()
        return trackInfo != null && trackInfo.getSubtitle().isNotEmpty()
    }

    private fun hideExoInternalSubtitle() {
        exoInternalSubtitle = false
        exoCues.clear()
        mController?.mExoSubtitleView?.setCues(exoCues)
        mController?.mExoSubtitleView?.visibility = View.GONE
    }

    private fun onExoCues(cues: List<Cue>?) {
        if (!isAdded || !exoInternalSubtitle) return
        exoCues.clear()
        if (cues != null) exoCues.addAll(cues)
        requireActivity().runOnUiThread {
            applyExoSubtitleSettings()
        }
    }

    private fun applyExoSubtitleSettings() {
        val exoView = mController?.mExoSubtitleView ?: return
        if (!exoInternalSubtitle) return
        val scale = SubtitleHelper.getExoSubtitleScale() / 100f
        val position = SubtitleHelper.getExoSubtitlePosition()
        exoView.setFractionalTextSize(0.0533f * scale)
        exoView.setBottomPaddingFraction(limit(0.08f + position / 100f, 0f, 0.9f))

        val displayCues = ArrayList<Cue>()
        for (cue in exoCues) {
            if (cue.bitmap == null) {
                displayCues.add(cue)
                continue
            }
            val builder = cue.buildUpon()
            if (cue.size != Cue.DIMEN_UNSET) {
                builder.setSize(limit(cue.size * scale, 0f, 1f))
            }
            if (cue.bitmapHeight != Cue.DIMEN_UNSET) {
                builder.setBitmapHeight(limit(cue.bitmapHeight * scale, 0f, 1f))
            }
            if (cue.line != Cue.DIMEN_UNSET) {
                builder.setLine(limit(cue.line - position / 100f, 0f, 1f), cue.lineType)
            }
            displayCues.add(builder.build())
        }
        exoView.setCues(displayCues)
    }

    private fun limit(value: Float, min: Float, max: Float): Float {
        return Math.max(min, Math.min(max, value))
    }

    fun setTip(msg: String, loading: Boolean, err: Boolean) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            mPlayLoadTip?.text = msg
            mPlayLoadTip?.visibility = View.VISIBLE
            mPlayLoading?.visibility = if (loading) View.VISIBLE else View.GONE
            mPlayLoadErr?.visibility = if (err) View.VISIBLE else View.GONE
        }
    }

    fun hideTip() {
        mPlayLoadTip?.visibility = View.GONE
        mPlayLoading?.visibility = View.GONE
        mPlayLoadErr?.visibility = View.GONE
    }

    fun hideTipOnUiThread() {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            hideTip()
        }
    }

    fun errorWithRetry(err: String, finish: Boolean) {
        if (isPlaybackStarted()) {
            cancelPlayTimeout()
            hideTipOnUiThread()
            return
        }
        if (!autoRetry()) {
            stopMusicSessionForFailedPlayback()
            if (!isAdded) return
            requireActivity().runOnUiThread {
                setTip(err, false, true)
                if (finish) {
                    Toast.makeText(context ?: requireContext(), err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopMusicSessionForFailedPlayback() {
        switchingPlayback = false
        audioPlayback = false
        MusicPlaybackService.stop(context, this)
    }

    fun playUrl(url: String, headers: HashMap<String, String>?) {
        startSwitchLinePlayTimeout()
        val targetUrl = attachProxySiteKey(url)
        if (!targetUrl.startsWith("data:application")) {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, targetUrl))
        }
        if (!Hawk.get(HawkConfig.M3U8_PURIFY, false)) {
            goPlayUrl(targetUrl, headers)
            return
        }
        if (targetUrl.startsWith("http://127.0.0.1") || !targetUrl.contains(".m3u8")) {
            goPlayUrl(targetUrl, headers)
            return
        }
        val flag = mVodInfo?.playFlag ?: ""
        if (DefaultConfig.noAd(flag)) {
            goPlayUrl(targetUrl, headers)
            return
        }
        LOG.i("echo-playM3u8:$targetUrl")
        mController?.playM3u8(targetUrl, headers)
    }

    fun goPlayUrl(url: String?, headers: HashMap<String, String>?) {
        LOG.i("echo-goPlayUrl:$url")
        if (TextUtils.isEmpty(url)) {
            handleResolvePlayUrlFailed("获取播放地址为空")
            return
        }
        if (autoRetryCount == 0) webPlayUrl = url
        if (mActivity == null || !isAdded) return
        val finalUrl = url ?: return
        requireActivity().runOnUiThread {
            stopParse()
            val videoView = mVideoView
            if (videoView != null) {
                var playUrlStr = finalUrl
                try {
                    val playerType = mVodPlayerCfg?.optInt("pl", 2) ?: 2
                    if (playerType >= 10) {
                        videoView.release()
                        val info = mVodInfo
                        val vs = info?.seriesMap?.get(info.playFlag)?.get(info.playIndex)
                        val playTitle = (info?.name ?: "") + " " + (vs?.name ?: "")
                        setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false)
                        val progress = getSavedProgress(progressKey ?: "")
                        val callResult = PlayerHelper.runExternalPlayer(playerType, requireActivity(), playUrlStr, playTitle, playSubtitle, headers, progress)
                        setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + if (callResult) "成功" else "失败", callResult, !callResult)
                        return@runOnUiThread
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                playTimeoutBasePosition = getSavedProgress(progressKey ?: "")
                val forceExoPlayer = playUrlStr.startsWith("data:application/dash+xml;base64,") ||
                        playUrlStr.contains(".mpd") || playUrlStr.contains("type=mpd")
                if (playUrlStr.startsWith("data:application/dash+xml;base64,")) {
                    PlayerHelper.updateCfg(videoView, mVodPlayerCfg, 2)
                    App.getInstance().dashData = playUrlStr.split("base64,").toTypedArray()[1]
                    playUrlStr = ControlManager.get().getAddress(true) + "dash/proxy.mpd"
                } else if (playUrlStr.contains(".mpd") || playUrlStr.contains("type=mpd")) {
                    PlayerHelper.updateCfg(videoView, mVodPlayerCfg, 2)
                } else {
                    PlayerHelper.updateCfg(videoView, mVodPlayerCfg)
                }
                mController?.hidePauseRoot()
                val reusePlayer = !forceExoPlayer && videoView.getMediaPlayer() != null
                if (!reusePlayer) hideTip()
                if (!reusePlayer && videoView.getMediaPlayer() != null) {
                    videoView.release()
                }
                videoView.setProgressKey(progressKey)
                if (headers != null) {
                    videoView.setUrl(playUrlStr, headers)
                } else {
                    videoView.setUrl(playUrlStr)
                }
                startSwitchLinePlayTimeout()
                if (reusePlayer) {
                    videoView.skipPositionWhenPlay(playTimeoutBasePosition.toInt())
                    videoView.replay(false)
                } else {
                    videoView.start()
                }
                mController?.resetSpeed()
            }
        }
    }

    private fun attachProxySiteKey(url: String): String {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(sourceKey)) return url
        if (!url.startsWith(ControlManager.get().getAddress(true) + "proxy?")) return url
        if (url.contains("siteKey=")) return url
        val key = sourceKey ?: ""
        return try {
            url + (if (url.contains("?")) "&" else "?") + "siteKey=" + URLEncoder.encode(key, "UTF-8")
        } catch (th: Throwable) {
            url + (if (url.contains("?")) "&" else "?") + "siteKey=" + key
        }
    }

    private fun initSubtitleView() {
        var trackInfo: TrackInfo? = null
        val mediaPlayer = mVideoView?.getMediaPlayer()
        val ctrl = mController ?: return
        val lyric = ctrl.mLyricView
        lyric?.setTextSize(if (previewMode) 16f else 24f)
        lyric?.visibility = View.GONE
        lyric?.reset()
        if (mediaPlayer != null) {
            lyric?.bindToMediaPlayer(mediaPlayer)
        }
        lyric?.setMergeSameTime(true)
        lyric?.setLyricMode(true)
        lyric?.setPlaySubtitleCacheKey(lyricCacheKey ?: "")
        val subView = ctrl.mSubtitleView
        subView?.hasInternal = false
        subView?.isInternal = false
        hideExoInternalSubtitle()
        if (mediaPlayer is IjkMediaPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
            if (trackInfo != null && trackInfo.getSubtitle().isNotEmpty()) {
                subView?.hasInternal = true
            }
            mediaPlayer.loadDefaultTrack(trackInfo, progressKey ?: "")
            mediaPlayer.setOnTimedTextListener(IMediaPlayer.OnTimedTextListener { _, text ->
                if (text == null) return@OnTimedTextListener
                if (subView?.isInternal == true) {
                    val subtitle = com.github.tvbox.osc.subtitle.model.Subtitle()
                    subtitle.content = text.getText() ?: ""
                    subView.onSubtitleChanged(subtitle)
                }
            })
        }
        if (mediaPlayer is ExoPlayer) {
            trackInfo = mediaPlayer.getTrackInfo()
            if (trackInfo != null && trackInfo.getSubtitle().isNotEmpty()) {
                subView?.hasInternal = true
                exoInternalSubtitle = true
                ctrl.mExoSubtitleView?.visibility = View.VISIBLE
                mediaPlayer.setInternalSubtitleDelay(SubtitleHelper.getTimeDelay())
                mediaPlayer.setOnCuesListener(ExoPlayer.OnCuesListener { cues ->
                    onExoCues(cues)
                })
                applyExoSubtitleSettings()
            }
            mediaPlayer.loadDefaultTrack(progressKey ?: "")
        }
        if (!TextUtils.isEmpty(playLyric)) {
            lyric?.setSubtitlePath(playLyric ?: "")
            lyric?.visibility = View.VISIBLE
        }
        val currentMp = mVideoView?.getMediaPlayer()
        if (currentMp != null) {
            subView?.bindToMediaPlayer(currentMp)
        }
        subView?.setPlaySubtitleCacheKey(subtitleCacheKey ?: "")
        val subtitlePathCache = CacheManager.getCache(MD5.string2MD5(subtitleCacheKey ?: "")) as? String
        if (!subtitlePathCache.isNullOrEmpty()) {
            hideExoInternalSubtitle()
            subView?.setSubtitlePath(subtitlePathCache)
        } else {
            val sub = playSubtitle
            if (!sub.isNullOrEmpty()) {
                hideExoInternalSubtitle()
                subView?.setSubtitlePath(sub)
            } else {
                if (subView?.hasInternal == true) {
                    if (mediaPlayer is ExoPlayer) {
                        mediaPlayer.setInternalSubtitleDelay(SubtitleHelper.getTimeDelay())
                        exoInternalSubtitle = true
                        ctrl.mExoSubtitleView?.visibility = View.VISIBLE
                        applyExoSubtitleSettings()
                    } else if (mediaPlayer is IjkMediaPlayer && trackInfo != null && trackInfo.getSubtitle().isNotEmpty()) {
                        subView.isInternal = true
                        val subtitleTrackList = trackInfo.getSubtitle()
                        val selectedIndex = trackInfo.getSubtitleSelected(true)
                        var hasMandarin = false
                        for (subtitleTrackInfoBean in subtitleTrackList) {
                            if ("国语" == subtitleTrackInfoBean.language) {
                                hasMandarin = true
                                if (selectedIndex != subtitleTrackInfoBean.trackId) {
                                    mediaPlayer.setTrack(subtitleTrackInfoBean.trackId)
                                    break
                                }
                            }
                        }
                        if (!hasMandarin && subtitleTrackList.isNotEmpty()) {
                            mediaPlayer.setTrack(subtitleTrackList[0].trackId)
                        }
                    }
                }
            }
        }
    }

    private fun getSubtitleUrl(obj: JSONObject?): String {
        if (obj == null) return ""
        var url = obj.optString("url", "")
        if (!TextUtils.isEmpty(url) && !FileUtils.hasExtension(url)) {
            val format = obj.optString("format", "")
            val name = obj.optString("name", "字幕")
            var ext = ".srt"
            if ("text/x-ssa" == format) {
                ext = ".ass"
            } else if ("text/vtt" == format) {
                ext = ".vtt"
            } else if ("text/lrc" == format) {
                ext = ".lrc"
            }
            val filename = name + (if (name.lowercase(Locale.ROOT).endsWith(ext)) "" else ext)
            url += "#" + (mController?.encodeUrl(filename) ?: filename)
        }
        return url
    }

    private fun isLyricSubtitle(name: String?): Boolean {
        if (TextUtils.isEmpty(name)) return false
        val value = name!!.lowercase(Locale.ROOT)
        return value.contains("lyric") || value.contains("lrc") || name.contains("歌词")
    }

    private fun clearLyricView() {
        val lyric = mController?.mLyricView ?: return
        lyric.visibility = View.GONE
        lyric.destroy()
        lyric.setText("")
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        playResultObserver = Observer<JSONObject> { info ->
            if (info == null) publishQuality(null)
            if (info != null) {
                try {
                    if (isStalePlayResult(info)) {
                        LOG.i("echo-ignore stale play result")
                        return@Observer
                    }
                    mHandler?.removeMessages(MSG_RESOLVE_PLAY_URL_TIMEOUT)
                    publishQuality(info)
                    webPlayUrl = null
                    progressKey = if (info.has("proKey")) info.optString("proKey") else null
                    val parse = info.optString("parse", "1") == "1"
                    val jx = info.optString("jx", "0") == "1"
                    playSubtitle = info.optString("subt", "")
                    playLyric = info.optString("lyric", "")
                    lyricCacheKey = if (info.has("lyricKey")) info.optString("lyricKey") else null
                    if (TextUtils.isEmpty(lyricCacheKey) && !TextUtils.isEmpty(progressKey)) {
                        lyricCacheKey = "$progressKey-lyric"
                    }
                    val lyrics = info.optJSONArray("lyrics")
                    if (lyrics != null && lyrics.length() > 0) {
                        playLyric = getSubtitleUrl(lyrics.optJSONObject(0))
                    }
                    val subtitles = info.optJSONArray("subs")
                    if (subtitles != null) {
                        for (i in 0 until subtitles.length()) {
                            val subObj = subtitles.optJSONObject(i) ?: continue
                            val url = getSubtitleUrl(subObj)
                            val name = subObj.optString("name", "")
                            if (isLyricSubtitle(name)) {
                                if (TextUtils.isEmpty(playLyric)) playLyric = url
                            } else if (TextUtils.isEmpty(playSubtitle)) {
                                playSubtitle = url
                            }
                        }
                    }
                    subtitleCacheKey = if (info.has("subtKey")) info.optString("subtKey") else null
                    val playUrl = info.optString("playUrl", "")
                    val flag = info.optString("flag")
                    val rawUrl = info.opt("url")
                    var url = rawUrl?.toString() ?: ""
                    if (url.startsWith("[")) {
                        url = mController?.firstUrlByArray(url) ?: url
                    }
                    var artwork = info.optString("artwork", "")
                    if (TextUtils.isEmpty(artwork) && !TextUtils.isEmpty(playLyric) && mVodInfo != null) {
                        artwork = mVodInfo?.pic ?: ""
                    }
                    playArtwork = artwork
                    mVideoView?.setArtwork(playArtwork)
                    val msg = info.optString("msg", "")
                    if (!TextUtils.isEmpty(msg)) {
                        handleResolvePlayUrlFailed(msg)
                        return@Observer
                    }
                    val danmaku = info.optString("danmaku", "").trim()
                    val danmuProgressKey = progressKey
                    webUserAgent = null
                    webHeaderMap = null
                    val headers = getHeaders(info)
                    if (headers != null) {
                        webHeaderMap = headers
                        webUserAgent = getHeaderValue(headers, "user-agent")?.trim()
                    }
                    if (parse || jx) {
                        val vipFlags = ApiConfig.get().getVipParseFlags() ?: emptyList()
                        val userJxList = (playUrl.isEmpty() && vipFlags.contains(flag)) || jx
                        initParse(flag, userJxList, playUrl, url)
                    } else {
                        mController?.showParse(false)
                        playUrl(playUrl + url, headers)
                    }
                    if (TextUtils.isEmpty(danmaku)) {
                        checkDanmu("")
                        searchDanmu("")
                    } else {
                        checkDanmu(danmaku, DanmuLoadController.LoadCallback {
                            if (TextUtils.equals(danmuProgressKey, progressKey)) {
                                searchDanmu("")
                            }
                        })
                    }
                } catch (th: Throwable) {
                    handleResolvePlayUrlFailed("获取播放信息错误")
                }
            } else {
                handleResolvePlayUrlFailed("获取播放信息错误")
            }
        }
        val observer = playResultObserver ?: return
        sourceViewModel?.playResult?.observeForever(observer)
    }

    fun selectQuality(position: Int): Boolean {
        val quality = qualityResult ?: return false
        return try {
            val urls = JSONArray(quality.optString("url"))
            val url = urls.optString(position * 2 + 1)
            if (TextUtils.isEmpty(url)) return false
            val playUrl = quality.optString("playUrl", "")
            val flag = quality.optString("flag")
            val parse = quality.optString("parse", "1") == "1"
            val jx = quality.optString("jx", "0") == "1"
            val headers = getHeaders(quality)
            if (parse || jx) {
                val vipFlags = ApiConfig.get().getVipParseFlags() ?: emptyList()
                val userJxList = (playUrl.isEmpty() && vipFlags.contains(flag)) || jx
                initParse(flag, userJxList, playUrl, url)
            } else {
                mController?.showParse(false)
                playUrl(playUrl + url, headers)
            }
            true
        } catch (th: Throwable) {
            false
        }
    }

    private fun publishQuality(info: JSONObject?) {
        try {
            val urls = JSONArray(if (info == null) "" else info.optString("url"))
            if (urls.length() < 4 || urls.length() % 2 != 0) throw JSONException("invalid quality urls")
            qualityResult = JSONObject(info.toString())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PLAY_QUALITY, qualityResult))
        } catch (th: Throwable) {
            qualityResult = null
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PLAY_QUALITY, null))
        }
    }

    private fun searchDanmu(danmaku: String) {
        val info = mVodInfo
        if (!TextUtils.isEmpty(danmaku) || !DanmakuApi.canSearch() || info == null) return
        val series = getCurrentSeries(info.playFlag, info.playIndex)
        val key = progressKey
        DanmakuApi.search(info.name, series?.name ?: "", object : DanmakuApi.SearchCallback {
            override fun onFound(url: String) {
                if (!TextUtils.equals(key, progressKey)) return
                checkDanmu(url)
            }

            override fun onNotFound() {
                if (!TextUtils.equals(key, progressKey)) return
                checkDanmu("")
            }
        })
    }

    fun isStalePlayResult(info: JSONObject): Boolean {
        val currentVod = mVodInfo ?: return false
        if (currentVod.seriesMap == null || TextUtils.isEmpty(progressKey)) return false
        val resultKey = info.optString("proKey", "")
        if (!TextUtils.isEmpty(resultKey) && progressKey != resultKey) return true
        val resultFlag = info.optString("flag", "")
        if (!TextUtils.isEmpty(resultFlag) && resultFlag != currentVod.playFlag) return true
        val sourceUrl = info.optString("key", "")
        if (!TextUtils.isEmpty(sourceUrl)) {
            val vs = getCurrentSeries(currentVod.playFlag, currentVod.playIndex)
            return vs != null && sourceUrl != vs.url
        }
        return false
    }

    fun setData(bundle: Bundle) {
        mVodInfo = App.getInstance().vodInfo
        sourceKey = bundle.getString("sourceKey")
        sourceBean = ApiConfig.get().getSource(sourceKey)
        ApiConfig.get().setCurrentPlaySourceKey(sourceKey)
        initPlayerCfg()
        triedLineFlags.clear()
        play(false)
    }

    private fun initData() {
    }

    fun initPlayerCfg() {
        val currentInfo = mVodInfo
        mVodPlayerCfg = try {
            JSONObject(currentInfo?.playerCfg ?: "{}")
        } catch (th: Throwable) {
            JSONObject()
        }
        try {
            val cfg = mVodPlayerCfg ?: JSONObject()
            if (!cfg.has("pl")) {
                val sb = sourceBean
                cfg.put("pl", if (sb == null || sb.playerType == -1) Hawk.get(HawkConfig.PLAY_TYPE, 2) else sb.playerType)
            }
            if (cfg.optInt("pl", 2) == 0) {
                cfg.put("pl", 2)
            }
            cfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0))
            if (!cfg.has("ijk")) {
                cfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"))
            }
            if (!cfg.has("sc")) {
                cfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0))
            }
            if (!cfg.has("sp")) {
                cfg.put("sp", 1.0)
            }
            if (!cfg.has("st")) {
                cfg.put("st", 0)
            }
            if (!cfg.has("et")) {
                cfg.put("et", 0)
            }
        } catch (ignored: Throwable) {
        }
        mController?.setPlayerConfig(mVodPlayerCfg)
    }

    fun onBackPressed(): Boolean {
        val act = activity ?: return false
        val requestedOrientation = act.requestedOrientation
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT ||
            requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        ) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            mController?.mLandscapePortraitBtn?.text = "竖屏"
        }
        return mController?.onBackPressed() ?: false
    }

    fun setExitingPreview(exitingPreview: Boolean) {
        this.exitingPreview = exitingPreview
    }

    private fun hasAudioOnlyPlayback(): Boolean {
        return java.lang.Boolean.TRUE == getAudioOnlyPlayback()
    }

    private fun getAudioOnlyPlayback(): Boolean? {
        val videoView = mVideoView ?: return null
        return try {
            val mediaPlayer = videoView.getMediaPlayer()
            var trackInfo: TrackInfo? = null
            if (mediaPlayer is IjkMediaPlayer) {
                trackInfo = mediaPlayer.getTrackInfo()
            } else if (mediaPlayer is ExoPlayer) {
                trackInfo = mediaPlayer.getTrackInfo()
            }
            if (trackInfo == null) return null
            trackInfo.getAudio().isNotEmpty() && trackInfo.getVideo().isEmpty()
        } catch (ignored: Throwable) {
            null
        }
    }

    private fun updateMusicSession() {
        val ctx = context ?: return
        if (!MusicPlaybackService.isSupported(ctx)) return
        if (switchingPlayback) return
        val audioOnly = getAudioOnlyPlayback()
        if (audioOnly != null) audioPlayback = audioOnly
        val currentVod = mVodInfo
        if (audioPlayback && TextUtils.isEmpty(playArtwork) && currentVod != null && !TextUtils.isEmpty(currentVod.pic)) {
            playArtwork = currentVod.pic
            mVideoView?.setArtwork(playArtwork)
        }
        val videoView = mVideoView
        if (currentVod == null || videoView == null || !audioPlayback ||
            videoView.getCurrentPlayState() == VideoView.STATE_ERROR ||
            videoView.getCurrentPlayState() == VideoView.STATE_PLAYBACK_COMPLETED
        ) {
            MusicPlaybackService.stop(ctx, this)
            audioPlayback = false
            return
        }
        val currentSeries = getCurrentSeries(currentVod.playFlag, currentVod.playIndex)
        val episode = if (currentSeries == null || TextUtils.isEmpty(currentSeries.name)) "" else currentSeries.name
        MusicPlaybackService.update(
            ctx, this,
            if (TextUtils.isEmpty(currentVod.name)) "TVBox" else currentVod.name,
            episode, currentVod.pic, videoView.getCurrentPosition(),
            videoView.getDuration(), videoView.isPlaying()
        )
    }

    fun resumeFromMediaSession() {
        mVideoView?.start()
        updateMusicSession()
    }

    fun pauseFromMediaSession() {
        mVideoView?.pause()
        updateMusicSession()
    }

    fun stopFromMediaSession() {
        mVideoView?.pause()
        MusicPlaybackService.stop(context, this)
    }

    fun seekFromMediaSession(position: Long) {
        mVideoView?.seekTo(position)
        updateMusicSession()
    }

    fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) {
            return mController?.onKeyEvent(event) ?: false
        }
        return false
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            return mController?.onKeyDown(keyCode, event) ?: false
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            return mController?.onKeyUp(keyCode, event) ?: false
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        if (mVideoView != null && !exitingPreview && !hasAudioOnlyPlayback()) {
            mVideoView?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        exitingPreview = false
        mVideoView?.resume()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            mVideoView?.pause()
        } else {
            mVideoView?.resume()
        }
        super.onHiddenChanged(hidden)
    }

    override fun onDestroyView() {
        audioPlayback = false
        switchingPlayback = false
        MusicPlaybackService.stop(context, this)
        val vm = sourceViewModel
        val obs = playResultObserver
        if (vm != null && obs != null) {
            vm.playResult.removeObserver(obs)
            playResultObserver = null
        }
        super.onDestroyView()
        ApiConfig.get().setCurrentPlaySourceKey("")
        cancelPlayTimeout()
        EventBus.getDefault().unregister(this)
        danmuLoadController?.destroy()
        danmuLoadController = null
        mVideoView?.release()
        mVideoView = null
        stopLoadWebView(true)
        stopParse()
        mController?.stopOther()
    }

    fun playNext(isProgress: Boolean) {
        triedLineFlags.clear()
        val currentVod = mVodInfo
        val seriesMap = currentVod?.seriesMap
        val hasNext = if (currentVod == null || seriesMap == null || seriesMap[currentVod.playFlag] == null) {
            false
        } else {
            currentVod.playIndex + 1 < (seriesMap[currentVod.playFlag]?.size ?: 0)
        }
        if (!hasNext) {
            Toast.makeText(context ?: requireContext(), "已经是最后一集了!", Toast.LENGTH_SHORT).show()
            return
        } else {
            currentVod?.playIndex = (currentVod?.playIndex ?: 0) + 1
        }
        reusePlayerOnSwitch = true
        play(false)
    }

    fun playPrevious() {
        triedLineFlags.clear()
        val currentVod = mVodInfo
        val seriesMap = currentVod?.seriesMap
        val hasPre = if (currentVod == null || seriesMap == null || seriesMap[currentVod.playFlag] == null) {
            false
        } else {
            (currentVod.playIndex - 1) >= 0
        }
        if (!hasPre) {
            Toast.makeText(context ?: requireContext(), "已经是第一集了!", Toast.LENGTH_SHORT).show()
            return
        }
        currentVod?.playIndex = (currentVod?.playIndex ?: 0) - 1
        reusePlayerOnSwitch = true
        play(false)
    }

    private fun showEpisodeDialog() {
        val currentVod = mVodInfo ?: return
        val seriesMap = currentVod.seriesMap
        if (!isAdded || seriesMap == null || TextUtils.isEmpty(currentVod.playFlag)) return
        val episodes = seriesMap[currentVod.playFlag]
        if (episodes == null || episodes.isEmpty()) return
        val episodeCount = episodes.size
        val currentPosition = Math.max(0, Math.min(currentVod.playIndex, episodeCount - 1))
        val groupStart: Int
        val dialogEpisodes: List<VodInfo.VodSeries>
        val selectedPosition: Int
        if (episodeCount > 200) {
            val groupCount = if (episodeCount <= 400) 60 else 120
            groupStart = (currentPosition / groupCount) * groupCount
            val groupEnd = Math.min(groupStart + groupCount, episodeCount)
            dialogEpisodes = ArrayList(episodes.subList(groupStart, groupEnd))
            selectedPosition = currentPosition - groupStart
        } else {
            groupStart = 0
            dialogEpisodes = episodes
            selectedPosition = currentPosition
        }
        val title = if (TextUtils.isEmpty(currentVod.name)) "选集" else currentVod.name + " 选集"
        val dialog = EpisodeDialog(requireContext(), title, dialogEpisodes, selectedPosition, object : EpisodeDialog.EpisodeSelectListener {
            override fun selectEpisode(position: Int) {
                val actualPosition = groupStart + position
                if (position < 0 || position >= dialogEpisodes.size ||
                    actualPosition >= episodes.size || actualPosition == currentVod.playIndex
                ) return
                triedLineFlags.clear()
                currentVod.playIndex = actualPosition
                reusePlayerOnSwitch = true
                play(false)
            }
        })
        dialog.show()
    }

    private fun restoreAutoSwitchedPlayer() {
        if (autoSwitchedPlayerType < 0) return
        releasePlayerOnSwitch = true
        try {
            val cfg = mVodPlayerCfg ?: JSONObject()
            LOG.i("echo-autoRetry restore player: " + cfg.optInt("pl", -1) + " -> " + autoSwitchedPlayerType)
            cfg.put("pl", autoSwitchedPlayerType)
            mVodInfo?.playerCfg = cfg.toString()
            mController?.setPlayerConfig(cfg)
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, cfg))
        } catch (th: Throwable) {
            th.printStackTrace()
        } finally {
            autoSwitchedPlayerType = -1
        }
    }

    fun autoRetry(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRetryTime > 60_000) {
            LOG.i("echo-reset-autoRetryCount")
            autoRetryCount = 0
            allowSwitchPlayer = true
            hasAutoSwitchedPlayer = false
            triedLineFlags.clear()
        }
        lastRetryTime = currentTime
        if (loadFoundVideoUrls.isNotEmpty()) {
            autoRetryFromLoadFoundVideoUrls()
            return true
        }
        val url = webPlayUrl
        if (url != null) {
            if (allowSwitchPlayer && !hasAutoSwitchedPlayer) {
                LOG.i("echo-autoRetry switch player and replay current url")
                val cfg = mVodPlayerCfg
                val playerType = cfg?.optInt("pl", -1) ?: -1
                val switchSkipped = mController?.switchPlayer() ?: false
                hasAutoSwitchedPlayer = true
                allowSwitchPlayer = false
                if (!switchSkipped) {
                    autoSwitchedPlayerType = playerType
                    stopParse()
                    initParseLoadFound()
                    mVideoView?.release()
                    playUrl(url, webHeaderMap)
                    return true
                }
            }
            LOG.i("echo-autoRetry current url failed after player switch, try next line")
            return tryNextLineIfEnabled()
        }
        return tryNextLineIfEnabled()
    }

    fun tryNextLineIfEnabled(): Boolean {
        restoreAutoSwitchedPlayer()
        if (allowAutoSwitchLine && Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true)) return tryNextLine()
        LOG.i("echo-autoRetry line switching disabled")
        autoRetryCount = 0
        allowSwitchPlayer = true
        hasAutoSwitchedPlayer = false
        triedLineFlags.clear()
        return false
    }

    fun tryNextLine(): Boolean {
        val currentVod = mVodInfo ?: return false
        val seriesMap = currentVod.seriesMap
        if (seriesMap == null || seriesMap.isEmpty()) {
            autoRetryCount = 0
            triedLineFlags.clear()
            return false
        }
        val currentFlag = currentVod.playFlag
        val currentIndex = Math.max(currentVod.playIndex, 0)
        val currentSeries = getCurrentSeries(currentFlag, currentIndex)
        if (!TextUtils.isEmpty(currentFlag)) {
            triedLineFlags.add(currentFlag ?: "")
        }
        val lineFlags = getLineFlagsInDisplayOrder()
        val currentLineIndex = findLineFlagIndex(lineFlags, currentFlag)
        val startLineIndex = if (currentLineIndex >= 0) currentLineIndex + 1 else 0
        var nextFlag: String? = null
        var nextIndex = 0
        for (i in startLineIndex until lineFlags.size) {
            val flag = lineFlags[i]
            val seriesList = seriesMap[flag]
            if (!triedLineFlags.contains(flag) && seriesList != null && seriesList.isNotEmpty()) {
                nextFlag = flag
                nextIndex = findSameEpisodeIndex(currentSeries, seriesList, currentIndex)
                break
            }
        }
        if (nextFlag == null) {
            LOG.i("echo-autoRetry all lines exhausted")
            triedLineFlags.clear()
            autoRetryCount = 0
            return requestDetailFallbackAfterLinesExhausted()
        }
        val flagToSwitch = nextFlag
        val preProgressKey = progressKey
        val savedProgress = if (TextUtils.isEmpty(preProgressKey)) 0L else getSavedProgress(preProgressKey ?: "")
        val currentPos = mVideoView?.getCurrentPosition() ?: 0L
        val preProgress = maxOf(savedProgress, currentPos)
        LOG.i("echo-autoRetry switch line: " + currentVod.playFlag + " -> " + flagToSwitch)
        if (isAdded) {
            requireActivity().runOnUiThread {
                Toast.makeText(context ?: requireContext(), "线路切换至$flagToSwitch", Toast.LENGTH_SHORT).show()
            }
        }
        currentVod.playFlag = flagToSwitch
        currentVod.playIndex = nextIndex
        autoRetryCount = 0
        allowSwitchPlayer = true
        hasAutoSwitchedPlayer = false
        inheritProgressKey = preProgressKey
        inheritProgress = preProgress
        reusePlayerOnSwitch = true
        play(false)
        return true
    }

    private fun requestDetailFallbackAfterLinesExhausted(): Boolean {
        val act = activity
        return if (act is DetailActivity) {
            act.startDetailFallbackAfterLinesExhausted()
        } else false
    }

    private fun getLineFlagsInDisplayOrder(): List<String> {
        val lineFlags = ArrayList<String>()
        val currentVod = mVodInfo ?: return lineFlags
        val seriesMap = currentVod.seriesMap ?: return lineFlags
        val seriesFlags = currentVod.seriesFlags
        if (seriesFlags != null) {
            for (flag in seriesFlags) {
                if (flag != null && !TextUtils.isEmpty(flag.name) && seriesMap.containsKey(flag.name) && !lineFlags.contains(flag.name)) {
                    lineFlags.add(flag.name ?: "")
                }
            }
        }
        for (flag in seriesMap.keys) {
            if (!TextUtils.isEmpty(flag) && !lineFlags.contains(flag)) {
                lineFlags.add(flag)
            }
        }
        return lineFlags
    }

    private fun findLineFlagIndex(lineFlags: List<String>?, currentFlag: String?): Int {
        if (lineFlags == null || TextUtils.isEmpty(currentFlag)) return -1
        for (i in lineFlags.indices) {
            if (currentFlag == lineFlags[i]) return i
        }
        return -1
    }

    private fun getCurrentSeries(flag: String?, index: Int): VodInfo.VodSeries? {
        val currentVod = mVodInfo ?: return null
        val seriesMap = currentVod.seriesMap
        if (flag == null || seriesMap == null) return null
        val currentList = seriesMap[flag]
        if (currentList == null || currentList.isEmpty()) return null
        val safeIndex = Math.max(0, Math.min(index, currentList.size - 1))
        return currentList[safeIndex]
    }

    private fun findSameEpisodeIndex(currentSeries: VodInfo.VodSeries?, targetList: List<VodInfo.VodSeries>?, fallbackIndex: Int): Int {
        if (targetList == null || targetList.isEmpty()) return 0
        if (targetList.size == 1) return 0
        if (currentSeries == null || TextUtils.isEmpty(currentSeries.name)) {
            return Math.max(0, Math.min(fallbackIndex, targetList.size - 1))
        }
        val currentEpisode = extractEpisodeNumber(currentSeries.name)
        var matchedIndex = -1
        var bestScore = 0
        for (i in targetList.indices) {
            val targetSeries = targetList[i]
            val score = getEpisodeMatchScore(currentSeries.name, currentEpisode, targetSeries.name)
            if (score > bestScore) {
                bestScore = score
                matchedIndex = i
            }
        }
        if (matchedIndex >= 0) return matchedIndex
        return Math.max(0, Math.min(fallbackIndex, targetList.size - 1))
    }

    private fun getEpisodeMatchScore(currentName: String?, currentEpisode: Int, targetName: String?): Int {
        if (TextUtils.isEmpty(currentName) || TextUtils.isEmpty(targetName)) return 0
        if (currentName.equals(targetName, ignoreCase = true)) return 100
        if (currentEpisode >= 0 && extractEpisodeNumber(targetName) == currentEpisode) return 80
        val currentLower = currentName!!.lowercase(Locale.ROOT)
        val targetLower = targetName!!.lowercase(Locale.ROOT)
        if (currentEpisode < 0 && currentName.length >= 2 && targetLower.contains(currentLower)) return 70
        if (currentEpisode < 0 && targetName.length >= 2 && currentLower.contains(targetLower)) return 60
        return 0
    }

    private fun extractEpisodeNumber(name: String?): Int {
        if (TextUtils.isEmpty(name)) return -1
        return try {
            var text = name!!.replace("\\[.*?\\]|\\(.*?\\)".toRegex(), "")
            text = text.replace("\\b(19|20)\\d{2}\\b".toRegex(), "")
            text = text.lowercase(Locale.ROOT).replace("2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4".toRegex(), "")
            val matcher = Pattern.compile("(?i)(?:ep|\\u7b2c|e|[\\-\\.\\s])\\s?(\\d{1,4})").matcher(text)
            if (matcher.find()) {
                matcher.group(1)?.toInt() ?: -1
            } else {
                val number = text.replace("\\D+".toRegex(), "")
                if (!TextUtils.isEmpty(number)) number.toInt() else -1
            }
        } catch (ignored: Exception) {
            -1
        }
    }

    fun autoRetryFromLoadFoundVideoUrls() {
        val videoUrl = loadFoundVideoUrls.poll() ?: return
        val header = loadFoundVideoUrlsHeader[videoUrl]
        playUrl(videoUrl, header)
    }

    fun initParseLoadFound() {
        loadFoundCount.set(0)
        loadFoundVideoUrls = LinkedList()
        loadFoundVideoUrlsHeader = HashMap()
    }

    fun setPlayTitle(show: Boolean) {
        val currentVod = mVodInfo
        if (show) {
            var playTitleInfo = ""
            if (currentVod != null) {
                val vs = currentVod.seriesMap?.get(currentVod.playFlag)?.get(currentVod.playIndex)
                playTitleInfo = currentVod.name + " " + (vs?.name ?: "")
            }
            mController?.setTitle(playTitleInfo)
        } else {
            mController?.setTitle("")
        }
    }

    fun play(reset: Boolean) {
        val currentVod = mVodInfo ?: return
        val reusePlayer = reusePlayerOnSwitch && !releasePlayerOnSwitch
        reusePlayerOnSwitch = false
        releasePlayerOnSwitch = false
        switchingPlayback = true
        audioPlayback = false
        playArtwork = ""
        exitingPreview = false
        val vs = currentVod.seriesMap?.get(currentVod.playFlag)?.get(currentVod.playIndex) ?: return
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, currentVod))
        if (reusePlayer) {
            mPlayLoadTip?.visibility = View.GONE
            mPlayLoadErr?.visibility = View.GONE
            mPlayLoading?.visibility = View.VISIBLE
        } else {
            setTip("正在获取播放信息", true, false)
        }
        val playTitleInfo = currentVod.name + " " + vs.name
        mController?.setTitle(playTitleInfo)

        stopParse()
        playbackStarted = false
        playTimeoutBasePosition = 0
        webPlayUrl = null
        webHeaderMap = null
        initParseLoadFound()
        allowSwitchPlayer = true
        hasAutoSwitchedPlayer = false
        mController?.stopOther()
        resetDanmuState()
        clearLyricView()
        mVideoView?.clearArtwork()
        if (mVideoView != null) {
            if (reusePlayer) {
                val previousPosition = mVideoView?.getCurrentPosition() ?: 0
                if (previousPosition > 0 && !TextUtils.isEmpty(progressKey)) {
                    CacheManager.save(MD5.string2MD5(progressKey ?: ""), previousPosition)
                }
                val mediaPlayer = mVideoView?.getMediaPlayer()
                mediaPlayer?.stop()
            } else {
                mVideoView?.release()
            }
        }
        ImgUtil.clearMemoryCache()
        subtitleCacheKey = currentVod.sourceKey + "-" + currentVod.id + "-" + currentVod.playFlag + "-" + currentVod.playIndex + "-" + vs.name + "-subt"
        progressKey = currentVod.sourceKey + currentVod.id + currentVod.playFlag + currentVod.playIndex + vs.name
        startResolvePlayUrlTimeout()
        if (reset) {
            CacheManager.delete(MD5.string2MD5(progressKey ?: ""), 0)
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey ?: ""), 0)
        } else {
            inheritProgressIfNeeded()
            try {
                val playerType = mVodPlayerCfg?.optInt("pl", 2) ?: 2
                if (playerType == 1) {
                    mController?.mSubtitleView?.visibility = View.VISIBLE
                } else {
                    mController?.mSubtitleView?.visibility = View.GONE
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        val vsUrl = vs.url ?: ""
        if (Jianpian.isJpUrl(vsUrl)) {
            mController?.showParse(false)
            if (vsUrl.startsWith("tvbox-xg:")) {
                playUrl(Jianpian.JPUrlDec(vsUrl.substring(9)), null)
            } else {
                playUrl(Jianpian.JPUrlDec(vsUrl), null)
            }
            return
        }
        if (Thunder.play(vsUrl, object : Thunder.ThunderCallback {
                override fun status(code: Int, info: String) {
                    if (code < 0) {
                        setTip(info, false, true)
                    } else {
                        setTip(info, true, false)
                    }
                }

                override fun list(urlMap: Map<Int, String>) {
                }

                override fun play(url: String) {
                    playUrl(url, null)
                }
            })) {
            mController?.showParse(false)
            return
        }
        val key = sourceKey ?: ""
        val progKey = progressKey ?: ""
        val subKey = subtitleCacheKey ?: ""
        sourceViewModel?.getPlay(key, currentVod.playFlag ?: "", progKey, vsUrl, subKey)
    }

    private fun inheritProgressIfNeeded() {
        try {
            if (TextUtils.isEmpty(inheritProgressKey) || TextUtils.isEmpty(progressKey)) return
            if (TextUtils.equals(inheritProgressKey, progressKey)) return
            if (inheritProgress <= 0) return
            val targetCache = CacheManager.getCache(MD5.string2MD5(progressKey ?: ""))
            if (targetCache == null) {
                CacheManager.save(MD5.string2MD5(progressKey ?: ""), inheritProgress)
            }
        } finally {
            inheritProgressKey = null
            inheritProgress = 0
        }
    }

    private fun initParse(flag: String?, useParse: Boolean, playUrl: String, url: String) {
        parseFlag = flag
        webUrl = url
        var parseBean: ParseBean? = null
        mController?.showParse(useParse)
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse()
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = ParseBean().apply {
                    setType(1)
                    setUrl(playUrl.substring(5))
                }
            } else if (playUrl.startsWith("parse:")) {
                val parseRedirect = playUrl.substring(6)
                for (pb in ApiConfig.get().getParseBeanList()) {
                    if (pb.getName() == parseRedirect) {
                        parseBean = pb
                        break
                    }
                }
            }
            if (parseBean == null) {
                parseBean = ParseBean().apply {
                    setType(0)
                    setUrl(playUrl)
                }
            }
        }
        if (parseBean != null) doParse(parseBean)
    }

    @Throws(JSONException::class)
    fun jsonParse(input: String, json: String): JSONObject? {
        val jsonPlayData = JSONObject(json)
        val playData = jsonPlayData.optJSONObject("data") ?: jsonPlayData
        var url = playData.optString("url", jsonPlayData.optString("url", ""))
        if (url.startsWith("//")) {
            url = "http:$url"
        }
        var parse = false
        if (url.startsWith("video://")) {
            url = url.substring(8)
            parse = true
        }
        url = DefaultConfig.checkReplaceProxy(url) ?: ""
        if (!url.startsWith("http") && !url.startsWith("data:application")) {
            return null
        }
        parse = parse || (playData.optInt("parse", jsonPlayData.optInt("parse", 0)) == 1)
        val headers = JSONObject()
        val headerMap = getHeaders(jsonPlayData)
        val dataHeaderMap = getHeaders(playData)
        if (headerMap != null) putHeaders(headers, headerMap)
        if (dataHeaderMap != null) putHeaders(headers, dataHeaderMap)
        val ua = playData.optString("user-agent", jsonPlayData.optString("user-agent", ""))
        if (ua.trim().isNotEmpty()) {
            headers.put("User-Agent", " $ua")
        }
        val referer = playData.optString("referer", jsonPlayData.optString("referer", ""))
        if (referer.trim().isNotEmpty()) {
            headers.put("Referer", " $referer")
        }
        val taskResult = JSONObject()
        taskResult.put("header", headers)
        taskResult.put("url", url)
        taskResult.put("parse", if (parse) 1 else 0)
        return taskResult
    }

    private fun getHeaders(obj: JSONObject?): HashMap<String, String>? {
        if (obj == null) return null
        val headers = HashMap<String, String>()
        appendHeaders(headers, obj.opt("header"))
        appendHeaders(headers, obj.opt("headers"))
        return if (headers.isEmpty()) null else headers
    }

    private fun appendHeaders(headers: HashMap<String, String>, rawHeaders: Any?) {
        if (rawHeaders == null || rawHeaders == JSONObject.NULL) return
        try {
            var json: JSONObject? = null
            if (rawHeaders is JSONObject) {
                json = rawHeaders
            } else if (rawHeaders is String) {
                val text = rawHeaders.trim()
                if (!TextUtils.isEmpty(text)) {
                    json = JSONObject(text)
                }
            }
            if (json == null) return
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!TextUtils.isEmpty(key)) {
                    headers[key] = json.optString(key, "")
                }
            }
        } catch (ignored: Throwable) {
        }
    }

    @Throws(JSONException::class)
    private fun putHeaders(target: JSONObject?, headers: HashMap<String, String>?) {
        if (target == null || headers == null) return
        for (key in headers.keys) {
            target.put(key, headers[key])
        }
    }

    private fun getHeaderValue(headers: HashMap<String, String>?, name: String?): String? {
        if (headers == null || name == null) return null
        for (key in headers.keys) {
            if (name.equals(key, ignoreCase = true)) {
                return headers[key]
            }
        }
        return null
    }

    fun startResolvePlayUrlTimeout() {
        cancelPlayTimeout()
        mHandler?.sendEmptyMessageDelayed(MSG_RESOLVE_PLAY_URL_TIMEOUT, getResolvePlayUrlTimeoutMs())
    }

    private fun getResolvePlayUrlTimeoutMs(): Long {
        val sb = sourceBean ?: return RESOLVE_PLAY_URL_TIMEOUT_MS
        return Math.max(RESOLVE_PLAY_URL_TIMEOUT_MS, (sb.playTimeoutSeconds + 1L) * 1000L)
    }

    fun startSwitchLinePlayTimeout() {
        if (!allowAutoSwitchLine) {
            cancelPlayTimeout()
            return
        }
        cancelPlayTimeout()
        LOG.i("echo-switchLinePlay start timeout")
        mHandler?.sendEmptyMessageDelayed(MSG_SWITCH_LINE_PLAY_TIMEOUT, SWITCH_LINE_PLAY_TIMEOUT_MS)
    }

    fun cancelSwitchLinePlayTimeout() {
        cancelPlayTimeout()
    }

    fun cancelPlayTimeout() {
        mHandler?.removeMessages(MSG_RESOLVE_PLAY_URL_TIMEOUT)
        mHandler?.removeMessages(MSG_SWITCH_LINE_PLAY_TIMEOUT)
    }

    fun setAutoSwitchLineEnabled(enabled: Boolean) {
        allowAutoSwitchLine = enabled
        if (!enabled) {
            cancelPlayTimeout()
            triedLineFlags.clear()
        }
    }

    fun setPreviewMode(previewMode: Boolean) {
        this.previewMode = previewMode
        val ctrl = mController
        if (ctrl != null) {
            ctrl.setPreviewMode(previewMode)
            ctrl.mLyricView?.setTextSize(if (previewMode) 16f else 24f)
        }
    }

    fun pauseForHidden() {
        cancelPlayTimeout()
        stopParse()
        playbackStarted = false
        mVideoView?.pause()
        mVideoView?.release()
        mController?.stopOther()
        resetDanmuState()
        webPlayUrl = null
        webHeaderMap = null
        initParseLoadFound()
    }

    fun markPlaybackStarted() {
        playbackStarted = true
        cancelPlayTimeout()
    }

    fun isPlaybackStarted(): Boolean {
        if (playbackStarted) return true
        val videoView = mVideoView ?: return false
        val state = videoView.getCurrentPlayState()
        return isStartedPlayState(state) || hasPlaybackProgress(videoView.getCurrentPosition()) || videoView.isPlaying()
    }

    fun isStartedPlayState(state: Int): Boolean {
        return state == VideoView.STATE_PREPARED || state == VideoView.STATE_BUFFERED || state == VideoView.STATE_PLAYING
    }

    fun hasPlaybackProgress(progress: Long): Boolean {
        return progress > Math.max(playTimeoutBasePosition, 0) + 1000
    }

    fun handleResolvePlayUrlTimeout() {
        LOG.i("echo-resolvePlayUrl timeout, try next line")
        sourceViewModel?.cancelPlayRequest()
        stopParse()
        if (!tryNextLineIfEnabled()) {
            stopMusicSessionForFailedPlayback()
            setTip("获取播放地址超时", false, true)
        }
    }

    fun handleResolvePlayUrlFailed(err: String) {
        LOG.i("echo-resolvePlayUrl failed, try next line: $err")
        sourceViewModel?.cancelPlayRequest()
        stopParse()
        if (tryNextLineIfEnabled()) return
        cancelPlayTimeout()
        stopMusicSessionForFailedPlayback()
        setTip(err, false, true)
    }

    fun handleSwitchLinePlayTimeout() {
        val state = mVideoView?.getCurrentPlayState() ?: -1
        LOG.i("echo-switchLinePlay timeout state: $state, started: $playbackStarted")
        if (isPlaybackStarted()) {
            cancelPlayTimeout()
            hideTipOnUiThread()
            return
        }
        LOG.i("echo-switchLinePlay timeout, try next line")
        stopParse()
        if (hasAutoSwitchedPlayer) {
            if (!tryNextLineIfEnabled()) {
                stopMusicSessionForFailedPlayback()
                setTip("播放超时", false, true)
            }
            return
        }
        if (!autoRetry()) {
            stopMusicSessionForFailedPlayback()
            setTip("播放超时", false, true)
        }
    }

    fun stopParse() {
        mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
        stopLoadWebView(false)
        OkGo.getInstance().cancelTag("play")
        OkGo.getInstance().cancelTag("json_jx")
        val pool = parseThreadPool
        if (pool != null) {
            try {
                pool.shutdown()
                parseThreadPool = null
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    private fun doParse(pb: ParseBean) {
        stopParse()
        initParseLoadFound()
        if (pb.getType() == 4) {
            parseMix(pb, true)
        } else if (pb.getType() == 0) {
            setTip("正在嗅探播放地址", true, false)
            mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
            mHandler?.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000)
            val ext = pb.getExt()
            if (ext != null) {
                try {
                    val reqHeaders = HashMap<String, String>()
                    val jsonObject = JSONObject(ext)
                    val headerMap = getHeaders(jsonObject)
                    if (headerMap != null) {
                        for (key in headerMap.keys) {
                            if (key.equals("user-agent", ignoreCase = true)) {
                                webUserAgent = headerMap[key]?.trim()
                            } else {
                                reqHeaders[key] = headerMap[key] ?: ""
                            }
                        }
                        if (reqHeaders.isNotEmpty()) webHeaderMap = reqHeaders
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            loadWebView((pb.getUrl() ?: "") + (webUrl ?: ""))
        } else if (pb.getType() == 1) {
            setTip("正在解析播放地址", true, false)
            val reqHeaders = HttpHeaders()
            try {
                val jsonObject = JSONObject(pb.getExt() ?: "{}")
                val headerMap = getHeaders(jsonObject)
                if (headerMap != null) {
                    for (key in headerMap.keys) {
                        reqHeaders.put(key, headerMap[key])
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            val encodedWebUrl = mController?.encodeUrl(webUrl ?: "") ?: (webUrl ?: "")
            OkGo.get<String>((pb.getUrl() ?: "") + encodedWebUrl)
                .tag("json_jx")
                .headers(reqHeaders)
                .execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        return body?.string() ?: throw IllegalStateException("网络请求错误")
                    }

                    override fun onSuccess(response: Response<String>) {
                        val json = response.body() ?: return
                        try {
                            val rs = jsonParse(webUrl ?: "", json)
                            if (rs == null) {
                                errorWithRetry("解析错误", false)
                                return
                            }
                            val headers = getHeaders(rs)
                            if (rs.optInt("parse", 0) == 1) {
                                webHeaderMap = headers
                                if (headers != null) {
                                    webUserAgent = getHeaderValue(headers, "user-agent")?.trim()
                                }
                                loadWebView(DefaultConfig.checkReplaceProxy(rs.getString("url") ?: "") ?: "")
                            } else {
                                playUrl(rs.getString("url"), headers)
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            errorWithRetry("解析错误", false)
                        }
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        errorWithRetry("解析错误", false)
                    }
                })
        } else if (pb.getType() == 2) {
            setTip("正在解析播放地址", true, false)
            val pool = Executors.newSingleThreadExecutor()
            parseThreadPool = pool
            val jxs = LinkedHashMap<String, String>()
            for (p in ApiConfig.get().getParseBeanList()) {
                if (p.getType() == 1) {
                    val pName = p.getName() ?: continue
                    jxs[pName] = p.mixUrl()
                }
            }
            pool.execute {
                val rs = ApiConfig.get().jsonExt(pb.getUrl() ?: "", jxs, webUrl ?: "")
                if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
                    setTip("解析错误", false, true)
                } else {
                    val headers = getHeaders(rs)
                    if (rs.has("jxFrom")) {
                        if (!isAdded) return@execute
                        requireActivity().runOnUiThread {
                            Toast.makeText(context ?: requireContext(), "解析来自:" + rs.optString("jxFrom"), Toast.LENGTH_SHORT).show()
                        }
                    }
                    val parseWV = rs.optInt("parse", 0) == 1
                    if (parseWV) {
                        val wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""))
                        loadUrl(wvUrl ?: "")
                    } else {
                        playUrl(rs.optString("url", ""), headers)
                    }
                }
            }
        } else if (pb.getType() == 3) {
            parseMix(pb, false)
        }
    }

    private fun parseMix(pb: ParseBean, isSuper: Boolean) {
        setTip("正在解析播放地址", true, false)
        val pool = Executors.newSingleThreadExecutor()
        parseThreadPool = pool
        val jxs = LinkedHashMap<String, HashMap<String, String>>()
        val jsonJxs = LinkedHashMap<String, String>()
        var extendName = ""
        for (p in ApiConfig.get().getParseBeanList()) {
            val data = HashMap<String, String>()
            data["url"] = p.getUrl() ?: ""
            if (p.getUrl() == pb.getUrl()) {
                extendName = p.getName() ?: ""
            }
            data["type"] = p.getType().toString()
            data["ext"] = p.getExt() ?: ""
            val pName = p.getName() ?: continue
            jxs[pName] = data
            if (p.getType() == 1) {
                jsonJxs[pName] = p.mixUrl()
            }
        }
        val finalExtendName = extendName
        pool.execute {
            if (isSuper) {
                val rs = SuperParse.parse(jxs, (parseFlag ?: "") + "123", webUrl ?: "")
                if (!rs.has("url") || rs.optString("url").isEmpty()) {
                    setTip("解析错误", false, true)
                } else {
                    if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                        if (rs.has("ua")) {
                            webUserAgent = rs.optString("ua").trim()
                        }
                        setTip("超级解析中", true, false)
                        if (!isAdded) return@execute
                        requireActivity().runOnUiThread {
                            val mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""))
                            stopParse()
                            mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
                            mHandler?.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000)
                            loadWebView(mixParseUrl ?: "")
                        }
                        parseThreadPool?.execute {
                            val res = SuperParse.doJsonJx(webUrl ?: "")
                            rsJsonJX(res, true)
                        }
                    } else {
                        rsJsonJX(rs, false)
                    }
                }
            } else {
                val rs = ApiConfig.get().jsonExtMix((parseFlag ?: "") + "111", pb.getUrl() ?: "", finalExtendName, jxs, webUrl ?: "")
                if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
                    setTip("解析错误", false, true)
                } else {
                    if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                        if (rs.has("ua")) {
                            webUserAgent = rs.optString("ua").trim()
                        }
                        if (!isAdded) return@execute
                        requireActivity().runOnUiThread {
                            val mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""))
                            stopParse()
                            setTip("正在嗅探播放地址", true, false)
                            mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
                            mHandler?.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000)
                            loadWebView(mixParseUrl ?: "")
                        }
                    } else {
                        rsJsonJX(rs, false)
                    }
                }
            }
        }
    }

    private fun rsJsonJX(rs: JSONObject?, isSuper: Boolean) {
        if (isSuper) {
            if (rs == null || !rs.has("url")) return
            stopLoadWebView(false)
        }
        val headers = getHeaders(rs)
        if (rs != null && rs.has("jxFrom")) {
            if (!isAdded) return
            requireActivity().runOnUiThread {
                Toast.makeText(context ?: requireContext(), "解析来自:" + rs.optString("jxFrom"), Toast.LENGTH_SHORT).show()
            }
        }
        playUrl(rs?.optString("url", "") ?: "", headers)
    }

    val player: MyVideoView?
        get() = mVideoView

    val mediaInfo: JsonObject
        get() {
            val info = JsonObject()
        val videoView = mVideoView ?: return info
        try {
            val playState = videoView.getCurrentPlayState()
            val state = if (videoView.isPlaying()) {
                3
            } else if (playState == VideoView.STATE_BUFFERING) {
                1
            } else if (playState == VideoView.STATE_PAUSED || playState == VideoView.STATE_PREPARED || playState == VideoView.STATE_BUFFERED) {
                2
            } else {
                -1
            }
            info.addProperty("state", state)
            info.addProperty("speed", videoView.getSpeed())
            info.addProperty("duration", videoView.getDuration())
            info.addProperty("position", videoView.getCurrentPosition())
            info.addProperty("url", webPlayUrl ?: "")
            var title = ""
            var artist = ""
            var artwork = ""
            val currentVod = mVodInfo
            if (currentVod != null) {
                title = if (TextUtils.isEmpty(currentVod.name)) "" else (currentVod.name ?: "")
                val series = getCurrentSeries(currentVod.playFlag, currentVod.playIndex)
                if (series != null && !TextUtils.isEmpty(series.name)) artist = series.name ?: ""
                artwork = if (TextUtils.isEmpty(currentVod.pic)) "" else (currentVod.pic ?: "")
            }
            if (!TextUtils.isEmpty(playArtwork)) artwork = playArtwork ?: ""
            info.addProperty("title", title)
            info.addProperty("artist", artist)
            info.addProperty("artwork", artwork)
        } catch (th: Throwable) {
            LOG.e("echo-media getMediaInfo error: " + th.message)
        }
        return info
    }

    fun loadWebView(url: String) {
        val ctx = context ?: return
        if (mSysWebView == null && mXwalkWebView == null) {
            val useSystemWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true)
            if (!useSystemWebView) {
                XWalkUtils.tryUseXWalk(ctx, object : XWalkUtils.XWalkState {
                    override fun success() {
                        initWebView(false)
                        loadUrl(url)
                    }

                    override fun fail() {
                        Toast.makeText(ctx, "XWalkView不兼容，已替换为系统自带WebView", Toast.LENGTH_SHORT).show()
                        initWebView(true)
                        loadUrl(url)
                    }

                    override fun ignore() {
                        Toast.makeText(ctx, "XWalkView运行组件未下载，已替换为系统自带WebView", Toast.LENGTH_SHORT).show()
                        initWebView(true)
                        loadUrl(url)
                    }
                })
            } else {
                initWebView(true)
                loadUrl(url)
            }
        } else {
            loadUrl(url)
        }
    }

    fun initWebView(useSystemWebView: Boolean) {
        val ctx = context ?: return
        if (useSystemWebView) {
            val sysWv = MyWebView(ctx)
            mSysWebView = sysWv
            configWebViewSys(sysWv)
        } else {
            val xwalkWv = MyXWalkView(ctx)
            mXwalkWebView = xwalkWv
            configWebViewX5(xwalkWv)
        }
    }

    fun loadUrl(url: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            val xwalk = mXwalkWebView
            if (xwalk != null) {
                xwalk.stopLoading()
                if (webUserAgent != null) {
                    xwalk.settings.userAgentString = webUserAgent
                }
                val headers = webHeaderMap
                if (headers != null) {
                    xwalk.loadUrl(url, headers)
                } else {
                    xwalk.loadUrl(url)
                }
            }
            val sys = mSysWebView
            if (sys != null) {
                sys.stopLoading()
                if (webUserAgent != null) {
                    sys.settings.userAgentString = webUserAgent
                }
                val headers = webHeaderMap
                if (headers != null) {
                    sys.loadUrl(url, headers)
                } else {
                    sys.loadUrl(url)
                }
            }
        }
    }

    fun stopLoadWebView(destroy: Boolean) {
        if (mActivity == null || !isAdded) return
        requireActivity().runOnUiThread {
            val xwalk = mXwalkWebView
            if (xwalk != null) {
                xwalk.stopLoading()
                xwalk.loadUrl("about:blank")
                if (destroy) {
                    xwalk.clearCache(true)
                    xwalk.removeAllViews()
                    xwalk.onDestroy()
                    mXwalkWebView = null
                }
            }
            val sys = mSysWebView
            if (sys != null) {
                sys.stopLoading()
                sys.loadUrl("about:blank")
                if (destroy) {
                    sys.clearCache(true)
                    sys.removeAllViews()
                    sys.destroy()
                    mSysWebView = null
                }
            }
        }
    }

    fun checkVideoFormat(url: String): Boolean {
        return try {
            if (url.contains("url=http") || url.contains(".html")) {
                return false
            }
            val sb = sourceBean
            if (sb != null && sb.type == 3) {
                val sp = ApiConfig.get().getCSP(sb)
                if (sp != null && sp.manualVideoCheck()) {
                    return sp.isVideoFormat(url)
                }
            }
            VideoParseRuler.checkIsVideoForParse(webUrl, url)
        } catch (e: Exception) {
            false
        }
    }

    inner class MyWebView(context: Context) : WebView(context) {
        override fun setOverScrollMode(mode: Int) {
            super.setOverScrollMode(mode)
            val ctx = mContext
            if (ctx is Activity) {
                AutoSize.autoConvertDensityOfCustomAdapt(ctx, this@PlayFragment)
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return false
        }
    }

    inner class MyXWalkView(context: Context) : XWalkView(context) {
        override fun setOverScrollMode(mode: Int) {
            super.setOverScrollMode(mode)
            val ctx = mContext
            if (ctx is Activity) {
                AutoSize.autoConvertDensityOfCustomAdapt(ctx, this@PlayFragment)
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configWebViewSys(webView: WebView?) {
        if (webView == null) return
        val layoutParams = if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            ViewGroup.LayoutParams(800, 400)
        } else {
            ViewGroup.LayoutParams(1, 1)
        }
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.clearFocus()
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS
        if (!isAdded) return
        requireActivity().addContentView(webView, layoutParams)

        val settings = webView.settings
        settings.setNeedInitialFocus(false)
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.databaseEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptEnabled = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = false
        }
        settings.blockNetworkImage = !Hawk.get(HawkConfig.DEBUG_OPEN, false)
        settings.useWideViewPort = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.setSupportZoom(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.defaultTextEncodingName = "utf-8"
        settings.userAgentString = webView.settings.userAgentString

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                return false
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return true
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                return true
            }
        }
        val sysWebClient = SysWebClient()
        webView.webViewClient = sysWebClient
        webView.setBackgroundColor(Color.BLACK)
    }

    private inner class SysWebClient : WebViewClient() {
        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(webView: WebView, sslErrorHandler: SslErrorHandler, sslError: SslError) {
            sslErrorHandler.proceed()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return false
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            LOG.i("echo-onPageFinished url:$url")
            if (url != "about:blank") {
                val sb = sourceBean
                if (sb != null) {
                    mController?.evaluateScript(sb, url, view, null)
                }
            }
        }

        fun checkIsVideo(url: String, headers: HashMap<String, String>): WebResourceResponse? {
            if (url.endsWith("/favicon.ico")) {
                return if (url.startsWith("http://127.0.0.1")) {
                    WebResourceResponse("image/x-icon", "UTF-8", null)
                } else null
            }
            val isFilter = VideoParseRuler.isFilter(webUrl, url)
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:$url")
                return null
            }
            val ad = if (!loadedUrls.containsKey(url)) {
                val isAd = AdBlocker.isAd(url)
                loadedUrls[url] = isAd
                isAd
            } else {
                java.lang.Boolean.TRUE == loadedUrls[url]
            }
            if (!ad) {
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url)
                    loadFoundVideoUrlsHeader[url] = headers
                    LOG.i("echo-loadFoundVideoUrl:$url")
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false)
                        SuperParse.stopJsonJx()
                        val playTarget = loadFoundVideoUrls.poll() ?: url
                        mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
                        val cookie = CookieManager.getInstance().getCookie(playTarget)
                        if (!TextUtils.isEmpty(cookie)) headers["Cookie"] = " $cookie"
                        playUrl(playTarget, headers)
                    }
                }
            }
            return if (ad || loadFoundCount.get() > 0) {
                AdBlocker.createEmptyResource()
            } else null
        }

        @Deprecated("Deprecated in Java")
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            return null
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            LOG.i("echo-shouldInterceptRequest url:$url")
            val webHeaders = HashMap<String, String>()
            val hds = request.requestHeaders
            if (hds != null && hds.isNotEmpty()) {
                for (k in hds.keys) {
                    if (k.equals("user-agent", ignoreCase = true) ||
                        k.equals("referer", ignoreCase = true) ||
                        k.equals("origin", ignoreCase = true)
                    ) {
                        webHeaders[k] = " " + hds[k]
                    }
                }
            }
            return checkIsVideo(url, webHeaders)
        }

        override fun onLoadResource(webView: WebView, url: String) {
            super.onLoadResource(webView, url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configWebViewX5(webView: XWalkView?) {
        if (webView == null) return
        val layoutParams = if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            ViewGroup.LayoutParams(800, 400)
        } else {
            ViewGroup.LayoutParams(1, 1)
        }
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.clearFocus()
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS
        if (!isAdded) return
        requireActivity().addContentView(webView, layoutParams)

        val settings: XWalkSettings = webView.settings
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.databaseEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptEnabled = true

        settings.blockNetworkImage = !Hawk.get(HawkConfig.DEBUG_OPEN, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = false
        }
        settings.useWideViewPort = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.setSupportZoom(false)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.setBackgroundColor(Color.BLACK)
        webView.setUIClient(object : XWalkUIClient(webView) {
            override fun onConsoleMessage(view: XWalkView, message: String, lineNumber: Int, sourceId: String, messageType: ConsoleMessageType): Boolean {
                return false
            }

            override fun onJsAlert(view: XWalkView, url: String, message: String, result: XWalkJavascriptResult): Boolean {
                return true
            }

            override fun onJsConfirm(view: XWalkView, url: String, message: String, result: XWalkJavascriptResult): Boolean {
                return true
            }

            override fun onJsPrompt(view: XWalkView, url: String, message: String, defaultValue: String, result: XWalkJavascriptResult): Boolean {
                return true
            }
        })
        val x5WebClient = XWalkWebClient(webView)
        webView.setResourceClient(x5WebClient)
    }

    private inner class XWalkWebClient(view: XWalkView) : XWalkResourceClient(view) {
        override fun onDocumentLoadedInFrame(view: XWalkView, frameId: Long) {
            super.onDocumentLoadedInFrame(view, frameId)
        }

        override fun onLoadStarted(view: XWalkView, url: String) {
            super.onLoadStarted(view, url)
        }

        override fun onLoadFinished(view: XWalkView, url: String) {
            super.onLoadFinished(view, url)
            LOG.i("echo-onLoadFinished url:$url")
            if (url != "about:blank") {
                val sb = sourceBean
                if (sb != null) {
                    mController?.evaluateScript(sb, url, null, view)
                }
            }
        }

        override fun onProgressChanged(view: XWalkView, progressInPercent: Int) {
            super.onProgressChanged(view, progressInPercent)
        }

        override fun shouldInterceptLoadRequest(view: XWalkView, request: XWalkWebResourceRequest): XWalkWebResourceResponse? {
            val url = request.url.toString()
            LOG.i("echo-shouldInterceptLoadRequest url:$url")
            if (url.endsWith("/favicon.ico")) {
                return if (url.startsWith("http://127.0.0.1")) {
                    createXWalkWebResourceResponse("image/x-icon", "UTF-8", null)
                } else null
            }

            val isFilter = VideoParseRuler.isFilter(webUrl, url)
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:$url")
                return null
            }

            val ad = if (!loadedUrls.containsKey(url)) {
                val isAd = AdBlocker.isAd(url)
                loadedUrls[url] = isAd
                isAd
            } else {
                java.lang.Boolean.TRUE == loadedUrls[url]
            }
            if (!ad) {
                if (checkVideoFormat(url)) {
                    val webHeaders = HashMap<String, String>()
                    val hds = request.requestHeaders
                    if (hds != null && hds.isNotEmpty()) {
                        for (k in hds.keys) {
                            if (k.equals("user-agent", ignoreCase = true) ||
                                k.equals("referer", ignoreCase = true) ||
                                k.equals("origin", ignoreCase = true)
                            ) {
                                webHeaders[k] = " " + hds[k]
                            }
                        }
                    }
                    loadFoundVideoUrls.add(url)
                    loadFoundVideoUrlsHeader[url] = webHeaders
                    LOG.i("echo-loadFoundVideoUrl:$url")
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false)
                        SuperParse.stopJsonJx()
                        val playTarget = loadFoundVideoUrls.poll() ?: url
                        mHandler?.removeMessages(MSG_PARSE_TIMEOUT)
                        val cookie = CookieManager.getInstance().getCookie(playTarget)
                        if (!TextUtils.isEmpty(cookie)) webHeaders["Cookie"] = " $cookie"
                        playUrl(playTarget, webHeaders)
                    }
                }
            }
            return if (ad || loadFoundCount.get() > 0) {
                createXWalkWebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
            } else null
        }

        override fun shouldOverrideUrlLoading(view: XWalkView, s: String): Boolean {
            return false
        }

        override fun onReceivedSslError(view: XWalkView, callback: ValueCallback<Boolean>, error: SslError) {
            callback.onReceiveValue(true)
        }
    }
}
