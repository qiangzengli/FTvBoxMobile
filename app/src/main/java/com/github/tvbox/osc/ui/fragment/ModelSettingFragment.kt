package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.DanmakuApi
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.dialog.AboutDialog
import com.github.tvbox.osc.ui.dialog.ApiDialog
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog
import com.github.tvbox.osc.ui.dialog.BackupDialog
import com.github.tvbox.osc.ui.dialog.DanmuApiDialog
import com.github.tvbox.osc.ui.dialog.SearchRemoteTvDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.FileCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayList
import java.util.Arrays

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
class ModelSettingFragment : BaseLazyFragment() {
    private var tvDebugOpen: TextView? = null
    private var tvMediaCodec: TextView? = null
    private var tvParseWebView: TextView? = null
    private var tvPlay: TextView? = null
    private var tvRender: TextView? = null
    private var tvScale: TextView? = null
    private var tvApi: TextView? = null
    private var tvApiLine: TextView? = null
    private var llApi: View? = null
    private var llApiHistory: View? = null
    private var llApiLine: View? = null
    private var tvHomeApi: TextView? = null
    private var tvDns: TextView? = null
    private var tvHomeRec: TextView? = null
    private var tvHistoryNum: TextView? = null
    private var tvHistoryMerge: TextView? = null
    private var tvSearchView: TextView? = null
    private var tvShowPreviewText: TextView? = null
    private var tvFastSearchText: TextView? = null
    private var tvm3u8AdText: TextView? = null
    private var tvAutoSwitchLineText: TextView? = null
    private var tvRecStyleText: TextView? = null
    private var tvIjkCachePlay: TextView? = null
    private var tvHomeDefaultShow: TextView? = null
    private var apiDialog: ApiDialog? = null
    private var selectLocalLive = false
    private var tvDanmuOpenText: TextView? = null
    private var tvDanmuApiText: TextView? = null

    companion object {
        private const val REQUEST_LOCAL_CONFIG = 1001

        @JvmField
        var loadingSearchRemoteTvDialog: SearchRemoteTvDialog? = null

        @JvmField
        var remoteTvHostList: List<String>? = null

        @JvmField
        var foundRemoteTv: Boolean = false

        @JvmStatic
        fun newInstance(): ModelSettingFragment {
            return ModelSettingFragment().setArguments()
        }
    }

    fun setArguments(): ModelSettingFragment {
        return this
    }

    override fun getLayoutResID(): Int {
        return R.layout.fragment_model
    }

    override fun init() {
        val act = requireActivity()
        val ctx = requireContext()

        tvFastSearchText = findViewById(R.id.showFastSearchText)
        tvFastSearchText?.text = if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) "开启" else "关闭"
        tvm3u8AdText = findViewById(R.id.m3u8AdText)
        tvm3u8AdText?.text = if (Hawk.get(HawkConfig.M3U8_PURIFY, false)) "开启" else "关闭"
        tvDanmuOpenText = findViewById(R.id.danmuOpenText)
        tvDanmuOpenText?.text = if (DanmuHelper.isOpen()) "开启" else "关闭"
        tvDanmuApiText = findViewById(R.id.danmuApiText)
        refreshDanmuApiText()
        tvAutoSwitchLineText = findViewById(R.id.autoSwitchLineText)
        tvAutoSwitchLineText?.text = if (Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true)) "开启" else "关闭"
        tvRecStyleText = findViewById(R.id.showRecStyleText)
        tvRecStyleText?.text = if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) "多列海报" else "单列列表"
        tvShowPreviewText = findViewById(R.id.showPreviewText)
        tvShowPreviewText?.text = if (Hawk.get(HawkConfig.SHOW_PREVIEW, true)) "开启" else "关闭"
        tvDebugOpen = findViewById(R.id.tvDebugOpen)
        findViewById<View>(R.id.llDebug)?.visibility = View.VISIBLE
        tvParseWebView = findViewById(R.id.tvParseWebView)
        tvMediaCodec = findViewById(R.id.tvMediaCodec)
        tvPlay = findViewById(R.id.tvPlay)
        tvRender = findViewById(R.id.tvRenderType)
        tvScale = findViewById(R.id.tvScaleType)
        llApi = findViewById(R.id.llApi)
        llApiHistory = findViewById(R.id.llApiHistory)
        llApiLine = findViewById(R.id.llApiLine)
        tvApi = findViewById(R.id.tvApi)
        tvApiLine = findViewById(R.id.tvApiLine)
        tvHomeApi = findViewById(R.id.tvHomeApi)
        tvDns = findViewById(R.id.tvDns)
        tvHomeRec = findViewById(R.id.tvHomeRec)
        tvHistoryNum = findViewById(R.id.tvHistoryNum)
        tvHistoryMerge = findViewById(R.id.tvHistoryMerge)
        tvSearchView = findViewById(R.id.tvSearchView)
        tvIjkCachePlay = findViewById(R.id.tvIjkCachePlay)
        tvMediaCodec?.text = Hawk.get(HawkConfig.IJK_CODEC, "硬解码")
        tvDebugOpen?.text = if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) "已打开" else "已关闭"
        tvParseWebView?.text = if (Hawk.get(HawkConfig.PARSE_WEBVIEW, true)) "系统自带" else "XWalkView"
        tvApi?.text = Hawk.get(HawkConfig.API_URL, "")
        refreshApiLineText()

        tvDns?.text = OkGoHelper.dnsHttpsList.getOrNull(Hawk.get(HawkConfig.DOH_URL, 0)) ?: ""
        tvHomeRec?.text = getHomeRecName(Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC))
        tvHistoryNum?.text = HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0))
        tvHistoryMerge?.text = if (Hawk.get(HawkConfig.HISTORY_MERGE, false)) "开启" else "关闭"
        tvSearchView?.text = getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0))
        tvHomeApi?.text = ApiConfig.get().getHomeSourceBean().name ?: ""
        tvScale?.text = PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0))
        tvPlay?.text = PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 2))
        tvRender?.text = PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0))
        tvIjkCachePlay?.text = if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) "开启" else "关闭"
        tvHomeDefaultShow = findViewById(R.id.tvHomeText)
        tvHomeDefaultShow?.text = if (Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) "直播" else "点播"

        findViewById<View>(R.id.llDebug)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val isOpen = !Hawk.get(HawkConfig.DEBUG_OPEN, false)
            Hawk.put(HawkConfig.DEBUG_OPEN, isOpen)
            tvDebugOpen?.text = if (isOpen) "已打开" else "已关闭"
        }

        findViewById<View>(R.id.llParseWebVew)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true)
            Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem)
            tvParseWebView?.text = if (useSystem) "系统自带" else "XWalkView"
            if (!useSystem) {
                Toast.makeText(ctx, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show()
                val dialog = XWalkInitDialog(ctx)
                dialog.setOnListener(object : XWalkInitDialog.OnListener {
                    override fun onchange() {}
                })
                dialog.show()
            }
        }

        findViewById<View>(R.id.llBackup)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val dialog = BackupDialog(act)
            dialog.show()
        }

        findViewById<View>(R.id.llAbout)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val dialog = AboutDialog(act)
            dialog.show()
        }

        findViewById<View>(R.id.llWp)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val wallpaper = ApiConfig.get().wallpaper
            if (!wallpaper.isNullOrEmpty()) {
                OkGo.get<File>(wallpaper).execute(object : FileCallback(act.filesDir.absolutePath, "wp") {
                    override fun onSuccess(response: Response<File>) {
                        (act as? BaseActivity)?.changeWallpaper(true)
                    }
                })
            }
        }

        findViewById<View>(R.id.llWpRecovery)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val wp = File(act.filesDir.absolutePath + "/wp")
            if (wp.exists()) wp.delete()
            (act as? BaseActivity)?.changeWallpaper(true)
        }

        findViewById<View>(R.id.llHomeApi)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val sites = ApiConfig.get().getSwitchSourceBeanList()
            if (sites.isNotEmpty()) {
                val dialog = SelectDialog<SourceBean>(act)
                dialog.setTip("请选择首页数据源")
                var select = sites.indexOf(ApiConfig.get().getHomeSourceBean())
                if (select < 0) select = 0
                dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<SourceBean> {
                    override fun click(value: SourceBean, pos: Int) {
                        dialog.dismiss()
                        ApiConfig.get().setSourceBean(value)
                        tvHomeApi?.text = ApiConfig.get().getHomeSourceBean().name ?: ""
                        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE))
                    }

                    override fun getDisplay(valItem: SourceBean): String {
                        return valItem.name ?: ""
                    }
                }, object : DiffUtil.ItemCallback<SourceBean>() {
                    override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                        return oldItem === newItem
                    }

                    override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                        return oldItem.key == newItem.key
                    }
                }, sites, select)
                dialog.show()
            }
        }

        findViewById<View>(R.id.llDns)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val dohUrl = Hawk.get(HawkConfig.DOH_URL, 0)
            val dialog = SelectDialog<String>(act)
            dialog.setTip("请选择安全DNS")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<String> {
                override fun click(value: String, pos: Int) {
                    tvDns?.text = OkGoHelper.dnsHttpsList.getOrNull(pos) ?: ""
                    Hawk.put(HawkConfig.DOH_URL, pos)
                    OkGoHelper.reloadDns()
                    IjkMediaPlayer.toggleDotPort(pos > 0)
                }

                override fun getDisplay(valItem: String): String {
                    return valItem
                }
            }, object : DiffUtil.ItemCallback<String>() {
                override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                    return oldItem == newItem
                }
            }, OkGoHelper.dnsHttpsList, dohUrl)
            dialog.show()
        }

        findViewById<View>(R.id.llApi)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            apiDialog = ApiDialog(act)
            val dialog = apiDialog ?: return@setOnClickListener
            EventBus.getDefault().register(dialog)
            dialog.setOnListener(object : ApiDialog.OnListener {
                override fun onchange(api: String) {
                    val oldApi = Hawk.get(HawkConfig.API_URL, "")
                    Hawk.put(HawkConfig.API_URL, api)
                    if (!HistoryHelper.isApiLineHistory(api)) {
                        HistoryHelper.clearApiLineList()
                    }
                    tvApi?.text = api
                    refreshApiLineText()
                    if (oldApi != api) {
                        restartAppAfterConfigChanged()
                    }
                }

                override fun onLocalConfig(live: Boolean) {
                    openLocalConfig(live)
                }
            })
            dialog.setOnDismissListener {
                (act as? BaseActivity)?.hideSysBar()
                EventBus.getDefault().unregister(dialog)
                apiDialog = null
            }
            dialog.show()
        }

        findViewById<View>(R.id.llApiHistory)?.setOnClickListener {
            val history = Hawk.get(HawkConfig.API_HISTORY, ArrayList<String>())
            if (history.isEmpty()) return@setOnClickListener
            val current = Hawk.get(HawkConfig.API_URL, "")
            var idx = 0
            if (history.contains(current)) {
                idx = history.indexOf(current)
            }
            val dialog = ApiHistoryDialog(act)
            dialog.setTip("历史配置列表")
            dialog.setAdapter(object : ApiHistoryDialogAdapter.SelectDialogInterface {
                override fun click(value: String) {
                    val oldApi = Hawk.get(HawkConfig.API_URL, "")
                    if (!HistoryHelper.isApiLineHistory(value)) {
                        HistoryHelper.clearApiLineList()
                    }
                    Hawk.put(HawkConfig.API_URL, value)
                    Hawk.put(HawkConfig.LIVE_API_URL, value)
                    HistoryHelper.setLiveApiHistory(value)
                    tvApi?.text = value
                    refreshApiLineText()
                    dialog.dismiss()
                    if (oldApi != value) {
                        restartAppAfterConfigChanged()
                    }
                }

                override fun del(value: String, data: ArrayList<String>) {
                    Hawk.put(HawkConfig.API_HISTORY, data)
                }
            }, history, idx)
            dialog.show()
        }

        findViewById<View>(R.id.llApiLine)?.setOnClickListener {
            val apiLines = Hawk.get(HawkConfig.API_LINE_LIST, ArrayList<String>())
            if (apiLines.isEmpty()) {
                Toast.makeText(ctx, "线路列表为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val current = Hawk.get(HawkConfig.API_URL, "")
            var idx = 0
            for (i in apiLines.indices) {
                if (current == HistoryHelper.getApiLineUrl(apiLines[i])) {
                    idx = i
                    break
                }
            }
            val dialog = SelectDialog<String>(act)
            dialog.setTip("线路选择")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<String> {
                override fun click(value: String, pos: Int) {
                    val newApi = HistoryHelper.getApiLineUrl(value)
                    val oldApi = Hawk.get(HawkConfig.API_URL, "")
                    if (newApi.isEmpty()) return
                    Hawk.put(HawkConfig.API_URL, newApi)
                    Hawk.put(HawkConfig.LIVE_API_URL, newApi)
                    HistoryHelper.setLiveApiHistory(newApi)
                    tvApi?.text = newApi
                    refreshApiLineText()
                    dialog.dismiss()
                    if (oldApi != newApi) {
                        restartAppAfterConfigChanged()
                    }
                }

                override fun getDisplay(valItem: String): String {
                    return HistoryHelper.getApiLineName(valItem)
                }
            }, SelectDialogAdapter.stringDiff, apiLines, idx)
            dialog.show()
        }

        findViewById<View>(R.id.llMediaCodec)?.setOnClickListener { v ->
            val ijkCodes = ApiConfig.get().getIjkCodes()
            if (ijkCodes == null || ijkCodes.isEmpty()) return@setOnClickListener
            FastClickCheckUtil.check(v)
            var defaultPos = 0
            val ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "硬解码")
            for (j in ijkCodes.indices) {
                if (ijkSel == ijkCodes[j].getName()) {
                    defaultPos = j
                    break
                }
            }
            val dialog = SelectDialog<IJKCode>(act)
            dialog.setTip("请选择IJK解码")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<IJKCode> {
                override fun click(value: IJKCode, pos: Int) {
                    value.selected(true)
                    tvMediaCodec?.text = value.getName()
                }

                override fun getDisplay(valItem: IJKCode): String {
                    return valItem.getName() ?: ""
                }
            }, object : DiffUtil.ItemCallback<IJKCode>() {
                override fun areItemsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean {
                    return oldItem === newItem
                }

                override fun areContentsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean {
                    return oldItem.getName() == newItem.getName()
                }
            }, ijkCodes, defaultPos)
            dialog.show()
        }

        findViewById<View>(R.id.llScale)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0)
            val players = arrayListOf(0, 1, 2, 3, 4, 5)
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择默认画面缩放")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    Hawk.put(HawkConfig.PLAY_SCALE, value)
                    tvScale?.text = PlayerHelper.getScaleName(value)
                }

                override fun getDisplay(valItem: Int): String {
                    return PlayerHelper.getScaleName(valItem)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, players, defaultPos)
            dialog.show()
        }

        findViewById<View>(R.id.llPlay)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val playerType = Hawk.get(HawkConfig.PLAY_TYPE, 2)
            var defaultPos = 0
            val players = PlayerHelper.getExistPlayerTypes()
            val renders = ArrayList<Int>()
            for (p in players.indices) {
                renders.add(p)
                if (players[p] == playerType) {
                    defaultPos = p
                }
            }
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择默认播放器")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    val thisPlayerType = players[pos]
                    Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType)
                    tvPlay?.text = PlayerHelper.getPlayerName(thisPlayerType)
                    PlayerHelper.init()
                }

                override fun getDisplay(valItem: Int): String {
                    val type = players[valItem]
                    return PlayerHelper.getPlayerName(type)
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
        }

        findViewById<View>(R.id.llRender)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0)
            val renders = arrayListOf(0, 1)
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择默认渲染方式")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    Hawk.put(HawkConfig.PLAY_RENDER, value)
                    tvRender?.text = PlayerHelper.getRenderName(value)
                    PlayerHelper.init()
                }

                override fun getDisplay(valItem: Int): String {
                    return PlayerHelper.getRenderName(valItem)
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
        }

        findViewById<View>(R.id.llHomeRec)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)
            val types = arrayListOf(0, 1, 2)
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择首页列表数据")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    Hawk.put(HawkConfig.HOME_REC, value)
                    tvHomeRec?.text = getHomeRecName(value)
                }

                override fun getDisplay(valItem: Int): String {
                    return getHomeRecName(valItem)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, types, defaultPos)
            dialog.show()
        }

        findViewById<View>(R.id.llSearchView)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 0)
            val types = arrayListOf(0, 1)
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("请选择搜索视图")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    Hawk.put(HawkConfig.SEARCH_VIEW, value)
                    tvSearchView?.text = getSearchView(value)
                }

                override fun getDisplay(valItem: Int): String {
                    return getSearchView(valItem)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, types, defaultPos)
            dialog.show()
        }

        SettingActivity.callback = SettingActivity.DevModeCallback {
            findViewById<View>(R.id.llDebug)?.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.showPreview)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val enable = !Hawk.get(HawkConfig.SHOW_PREVIEW, true)
            Hawk.put(HawkConfig.SHOW_PREVIEW, enable)
            tvShowPreviewText?.text = if (enable) "开启" else "关闭"
        }

        findViewById<View>(R.id.llHistoryNum)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.HISTORY_NUM, 0)
            val types = arrayListOf(0, 1, 2)
            val dialog = SelectDialog<Int>(act)
            dialog.setTip("保留历史记录数量")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<Int> {
                override fun click(value: Int, pos: Int) {
                    Hawk.put(HawkConfig.HISTORY_NUM, value)
                    tvHistoryNum?.text = HistoryHelper.getHistoryNumName(value)
                }

                override fun getDisplay(valItem: Int): String {
                    return HistoryHelper.getHistoryNumName(valItem)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, types, defaultPos)
            dialog.show()
        }

        findViewById<View>(R.id.showFastSearch)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val enable = !Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)
            Hawk.put(HawkConfig.FAST_SEARCH_MODE, enable)
            tvFastSearchText?.text = if (enable) "开启" else "关闭"
        }

        findViewById<View>(R.id.m3u8Ad)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val isPurify = !Hawk.get(HawkConfig.M3U8_PURIFY, false)
            Hawk.put(HawkConfig.M3U8_PURIFY, isPurify)
            tvm3u8AdText?.text = if (isPurify) "开启" else "关闭"
        }

        findViewById<View>(R.id.danmuOpen)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val open = !DanmuHelper.isOpen()
            DanmuHelper.setOpen(open)
            tvDanmuOpenText?.text = if (open) "开启" else "关闭"
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, open))
        }

        findViewById<View>(R.id.danmuApi)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val dialog = DanmuApiDialog(act)
            dialog.setOnListener { refreshDanmuApiText() }
            dialog.show()
        }

        findViewById<View>(R.id.autoSwitchLine)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val enable = !Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true)
            Hawk.put(HawkConfig.AUTO_SWITCH_LINE, enable)
            tvAutoSwitchLineText?.text = if (enable) "开启" else "关闭"
        }

        findViewById<View>(R.id.llHomeRecStyle)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val dialog = SelectDialog<String>(act)
            dialog.setTip("首页布局")
            val modes = Arrays.asList("单列列表", "多列海报")
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<String> {
                override fun click(value: String, position: Int) {
                    Hawk.put(HawkConfig.HOME_REC_STYLE, position == 1)
                    tvRecStyleText?.text = value
                    dialog.dismiss()
                }

                override fun getDisplay(valItem: String): String {
                    return valItem
                }
            }, SelectDialogAdapter.stringDiff, modes, if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) 1 else 0)
            dialog.show()
        }

        findViewById<View>(R.id.llHistoryMerge)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val historyMerge = !Hawk.get(HawkConfig.HISTORY_MERGE, false)
            Hawk.put(HawkConfig.HISTORY_MERGE, historyMerge)
            tvHistoryMerge?.text = if (historyMerge) "开启" else "关闭"
        }

        // 下次进入
        findViewById<View>(R.id.tvHomeLive)?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val next = !Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)
            Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, next)
            tvHomeDefaultShow?.text = if (next) "直播" else "点播"
        }

        findViewById<View>(R.id.llIjkCachePlay)?.setOnClickListener { v -> onClickIjkCachePlay(v) }
        findViewById<View>(R.id.llClearCache)?.setOnClickListener { v -> onClickClearCache(v) }
    }

    private fun restartAppAfterConfigChanged() {
        val ctx = context ?: return
        Toast.makeText(ctx, "配置已切换,即将重新加载!", Toast.LENGTH_SHORT).show()
        clearConfigSwitchCache()
        Handler(Looper.getMainLooper()).postDelayed({
            val activity = mActivity
            if (activity != null && !activity.isFinishing) {
                activity.onBackPressed()
            }
        }, 2500)
    }

    private fun clearConfigSwitchCache() {
        try {
            SourceViewModel.clearRuntimeCache()
            LOG.i("echo-clear-config-switch-cache")
        } catch (e: Exception) {
            LOG.i("echo-clear-config-switch-cache-error:" + e.message)
            e.printStackTrace()
        }
    }

    private fun restartAppAfterCacheCleared() {
        val ctx = context ?: return
        Toast.makeText(ctx, "缓存已清空,即将回到主页!", Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            restartApp()
        }, 2500)
    }

    private fun refreshApiLineText() {
        val tv = tvApiLine ?: return
        val apiLines = Hawk.get(HawkConfig.API_LINE_LIST, ArrayList<String>())
        val current = Hawk.get(HawkConfig.API_URL, "")
        val showLine = HistoryHelper.isApiLineUrl(current)
        llApiLine?.visibility = if (showLine) View.VISIBLE else View.GONE
        updateApiRowWeight(showLine)
        var lineName = ""
        if (showLine) {
            for (apiLine in apiLines) {
                if (current == HistoryHelper.getApiLineUrl(apiLine)) {
                    lineName = HistoryHelper.getApiLineName(apiLine)
                    break
                }
            }
        }
        tv.text = lineName
    }

    private fun refreshDanmuApiText() {
        val tv = tvDanmuApiText ?: return
        if (DanmakuApi.isUseDefault()) {
            tv.text = "默认"
            return
        }
        val custom = Hawk.get(HawkConfig.DANMU_API, "")
        if (custom.isNotEmpty()) {
            tv.text = "自定义"
            return
        }
        val config = ApiConfig.get().danmaku
        tv.text = if (config.isNullOrEmpty()) "默认" else "接口"
    }

    private fun updateApiRowWeight(showLine: Boolean) {
        val row = llApi ?: return
        val params = row.layoutParams as? LinearLayout.LayoutParams ?: return
        params.weight = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            0f
        } else {
            if (showLine) 1.0f else 3.08f
        }
        row.layoutParams = params
        val historyView = llApiHistory
        if (historyView != null) {
            val historyParams = historyView.layoutParams as? LinearLayout.LayoutParams
            if (historyParams != null) {
                val margin = if (showLine) resources.getDimensionPixelSize(R.dimen.vs_5) else 0
                historyParams.rightMargin = margin
                historyParams.marginEnd = margin
                historyView.layoutParams = historyParams
            }
        }
    }

    private fun restartApp() {
        val context = mContext ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            System.exit(0)
        }
    }

    private fun onClickIjkCachePlay(v: View) {
        FastClickCheckUtil.check(v)
        val next = !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)
        Hawk.put(HawkConfig.IJK_CACHE_PLAY, next)
        tvIjkCachePlay?.text = if (next) "开启" else "关闭"
    }

    private fun openLocalConfig(live: Boolean) {
        selectLocalLive = live
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_LOCAL_CONFIG)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_LOCAL_CONFIG || resultCode != android.app.Activity.RESULT_OK || data == null || data.data == null) {
            return
        }
        val uri = data.data ?: return
        val api = localConfigToApi(uri)
        if (api.isEmpty()) {
            Toast.makeText(context, "读取本地配置失败", Toast.LENGTH_SHORT).show()
            return
        }
        apiDialog?.setLocalApi(api, selectLocalLive)
    }

    private fun localConfigToApi(uri: Uri): String {
        var path = copyUriToLocalConfig(uri)
        if (path.isEmpty()) {
            return ""
        }
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        if (path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length).replaceFirst("^/+".toRegex(), "")
        }
        path = copyUriToLocalConfig(uri)
        if (path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length).replaceFirst("^/+".toRegex(), "")
        }
        return ""
    }

    private fun getPathFromUri(uri: Uri): String? {
        val ctx = context ?: return null
        try {
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if ("com.android.externalstorage.documents" == uri.authority) {
                    val split = docId.split(":")
                    if (split.size > 1 && "primary".equals(split[0], ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                    }
                }
                if ("com.android.providers.downloads.documents" == uri.authority && docId.startsWith("raw:")) {
                    return docId.substring(4)
                }
            }
        } catch (ignored: Throwable) {
        }
        return null
    }

    private fun copyUriToLocalConfig(uri: Uri): String {
        val ctx = context ?: return ""
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            input = ctx.contentResolver.openInputStream(uri) ?: return ""
            val dir = ctx.getExternalFilesDir("config") ?: return ""
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, java.util.UUID.randomUUID().toString() + "-" + File(getDisplayName(uri)).name)
            output = FileOutputStream(file)
            val buffer = ByteArray(8192)
            var length: Int
            while (input.read(buffer).also { length = it } != -1) {
                output.write(buffer, 0, length)
            }
            return file.absolutePath
        } catch (th: Throwable) {
            th.printStackTrace()
            return ""
        } finally {
            try {
                output?.close()
            } catch (ignored: Throwable) {
            }
            try {
                input?.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun getDisplayName(uri: Uri): String {
        val ctx = context ?: return "local_config.json"
        var name = "local_config.json"
        var cursor: Cursor? = null
        try {
            cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val displayName = cursor.getString(index)
                    if (!displayName.isNullOrEmpty()) {
                        name = displayName
                    }
                }
            }
        } catch (ignored: Throwable) {
        } finally {
            cursor?.close()
        }
        return name
    }

    private fun onClickClearCache(v: View) {
        FastClickCheckUtil.check(v)
        val cachePath = FileUtils.getCachePath()
        val cacheDir = File(cachePath)
        Thread {
            try {
                ApiConfig.get().clearSpiderCache()
                if (cacheDir.exists()) FileUtils.cleanDirectory(cacheDir)
                FileUtils.clearSpiderCacheFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mActivity?.runOnUiThread {
                    restartAppAfterCacheCleared()
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SettingActivity.callback = null
    }

    fun getHomeRecName(type: Int): String {
        return when (type) {
            1 -> "站点推荐"
            2 -> "观看历史"
            else -> "豆瓣热播"
        }
    }

    fun getSearchView(type: Int): String {
        return if (type == 0) "文字列表" else "缩略图"
    }
}
