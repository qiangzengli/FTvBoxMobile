package com.github.tvbox.osc.ui.activity

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearSmoothScroller
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.SeriesAdapter
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter
import com.github.tvbox.osc.ui.dialog.DescDialog
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog
import com.github.tvbox.osc.ui.fragment.PlayFragment
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.util.SubtitleHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.lzy.okgo.OkGo
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import me.jessyan.autosize.utils.AutoSizeUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
class DetailActivity : BaseActivity() {

    companion object {
        private const val STATE_FULL_WINDOWS = "detail_full_windows"
        private const val DETAIL_FALLBACK_SEARCH_TAG = "detail_fallback_search"
        const val EXTRA_DETAIL_FALLBACK_CANDIDATES = "detailFallbackCandidates"
        private const val DETAIL_FALLBACK_MAX_SEARCH = 20
        private const val DETAIL_FALLBACK_SEARCH_TIMEOUT_MS = 8000L
        private const val DETAIL_FALLBACK_DETAIL_TIMEOUT_MS = 6000L
    }

    private var llLayout: LinearLayout? = null
    private var llPlayerFragmentContainer: FragmentContainerView? = null
    private var llPlayerFragmentContainerBlock: View? = null
    private var llPlayerPlace: View? = null
    @JvmField
    var playFragment: PlayFragment? = null
    private var thumbContainer: View? = null
    private var ivThumb: ImageView? = null
    private var tvName: TextView? = null
    private var tvYear: TextView? = null
    private var tvSite: TextView? = null
    private var tvArea: TextView? = null
    private var tvLang: TextView? = null
    private var tvType: TextView? = null
    private var tvActor: TextView? = null
    private var tvDirector: TextView? = null
    private var tvPlayUrl: TextView? = null
    private var tvDes: TextView? = null
    private var tvPlay: TextView? = null
    private var tvDesc: TextView? = null
    private var tvSeriesSort: TextView? = null
    private var tvQuickSearch: TextView? = null
    private var tvChangeSource: TextView? = null
    private var tvCollect: TextView? = null
    private var mGridViewFlag: TvRecyclerView? = null
    private var mGridViewQuality: TvRecyclerView? = null
    private var mGridView: TvRecyclerView? = null
    private var mSeriesGroupView: TvRecyclerView? = null
    private var mEmptyPlayList: LinearLayout? = null
    private var tvSeriesGroup: LinearLayout? = null
    private var sourceViewModel: SourceViewModel? = null
    private var mVideo: Movie.Video? = null
    private var vodInfo: VodInfo? = null
    private var seriesFlagAdapter: SeriesFlagAdapter? = null
    private var qualityAdapter: BaseQuickAdapter<String, BaseViewHolder>? = null
    private var seriesGroupAdapter: BaseQuickAdapter<String, BaseViewHolder>? = null
    private var seriesAdapter: SeriesAdapter? = null
    var vodId: String? = null
    var sourceKey: String = ""
    var firstsourceKey: String = ""
    private var fromCollect = false
    var seriesSelect = false
    private var seriesFlagFocus: View? = null
    private var isReverse = false
    private var preFlag = ""
    private var routeSwitchSeries: VodInfo.VodSeries? = null
    private var firstReverse = false
    private var mGridViewLayoutMgr: V7GridLayoutManager? = null
    private var mCheckSources: HashMap<String, String>? = null
    private val seriesGroupOptions = ArrayList<String>()
    private val qualityOptions = ArrayList<String>()
    private var currentSeriesGroupView: View? = null
    private var selectedSeriesGroupPosition = 0
    private var GroupCount = 0
    private var qualityPosition = 0
    var showPreview: Boolean = Hawk.get(HawkConfig.SHOW_PREVIEW, true)
    private var smoothScroller: LinearSmoothScroller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            fullWindows = savedInstanceState.getBoolean(STATE_FULL_WINDOWS, false)
        }
        super.onCreate(savedInstanceState)
    }

    override fun getLayoutResID(): Int {
        return R.layout.activity_detail
    }

    override fun init() {
        EventBus.getDefault().register(this)
        initView()
        initViewModel()
        initData()
    }

    private fun initView() {
        llLayout = findViewById(R.id.llLayout)
        llPlayerPlace = findViewById(R.id.previewPlayerPlace)
        llPlayerFragmentContainer = findViewById(R.id.previewPlayer)
        llPlayerFragmentContainerBlock = findViewById(R.id.previewPlayerBlock)
        applyPreviewRoundCorners()
        thumbContainer = findViewById(R.id.thumbContainer)
        ivThumb = findViewById(R.id.ivThumb)
        applyThumbPreviewStyle()
        tvName = findViewById(R.id.tvName)
        tvYear = findViewById(R.id.tvYear)
        tvSite = findViewById(R.id.tvSite)
        tvArea = findViewById(R.id.tvArea)
        tvLang = findViewById(R.id.tvLang)
        tvType = findViewById(R.id.tvType)
        tvActor = findViewById(R.id.tvActor)
        tvDirector = findViewById(R.id.tvDirector)
        tvPlayUrl = findViewById(R.id.tvPlayUrl)
        tvDes = findViewById(R.id.tvDes)
        tvPlay = findViewById(R.id.tvPlay)
        tvDesc = findViewById(R.id.tvDesc)
        tvSeriesSort = findViewById(R.id.mSeriesSortTv)
        tvCollect = findViewById(R.id.tvCollect)
        tvQuickSearch = findViewById(R.id.tvQuickSearch)
        tvChangeSource = findViewById(R.id.tvChangeSource)
        mEmptyPlayList = findViewById(R.id.mEmptyPlaylist)
        mGridView = findViewById(R.id.mGridView)
        mGridView?.setHasFixedSize(false)
        val layoutMgr = V7GridLayoutManager(this.mContext, if (isPhonePortrait()) 3 else 6)
        this.mGridViewLayoutMgr = layoutMgr
        mGridView?.layoutManager = layoutMgr

        smoothScroller = object : LinearSmoothScroller(mContext) {
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return 100f / displayMetrics.densityDpi
            }

            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                return mGridViewLayoutMgr?.computeScrollVectorForPosition(targetPosition)
            }
        }

        val sAdapter = SeriesAdapter(layoutMgr)
        seriesAdapter = sAdapter
        mGridView?.adapter = sAdapter
        mGridViewFlag = findViewById(R.id.mGridViewFlag)
        mGridViewFlag?.setHasFixedSize(true)
        mGridViewFlag?.layoutManager = V7LinearLayoutManager(this.mContext, 0, false)
        val sfAdapter = SeriesFlagAdapter()
        seriesFlagAdapter = sfAdapter
        mGridViewFlag?.adapter = sfAdapter
        mGridViewQuality = findViewById(R.id.mGridViewQuality)
        mGridViewQuality?.setHasFixedSize(true)
        mGridViewQuality?.layoutManager = V7LinearLayoutManager(this.mContext, 0, false)
        val qAdapter = object : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_series_flag, qualityOptions) {
            override fun convert(helper: BaseViewHolder, item: String) {
                helper.setText(R.id.tvSeriesFlag, item)
                helper.getView<View>(R.id.tvSeriesFlagSelect).visibility = if (helper.layoutPosition == qualityPosition) View.VISIBLE else View.GONE
                helper.itemView.nextFocusUpId = if (tvSeriesGroup?.visibility == View.VISIBLE) R.id.mSeriesSortTv else R.id.mGridViewFlag
                helper.itemView.nextFocusDownId = R.id.mGridView
            }
        }
        qualityAdapter = qAdapter
        mGridViewQuality?.adapter = qAdapter
        mGridViewQuality?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}
            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                val pf = playFragment ?: return
                if (position == qualityPosition) {
                    if (showPreview && !fullWindows && pf.player?.isPlaying() == true) enterFullPreview()
                    return
                }
                if (pf.selectQuality(position)) {
                    qualityPosition = position
                    qualityAdapter?.notifyDataSetChanged()
                }
            }
        })
        isReverse = false
        firstReverse = false
        preFlag = ""
        if (showPreview) {
            ensurePlayFragment()
            tvPlay?.text = "全屏"
        }
        llPlayerFragmentContainerBlock?.isFocusable = showPreview

        mSeriesGroupView = findViewById(R.id.mSeriesGroupView)
        tvSeriesGroup = findViewById(R.id.mSeriesGroupTv)
        mSeriesGroupView?.setHasFixedSize(true)
        mSeriesGroupView?.layoutManager = V7LinearLayoutManager(this.mContext, 0, false)
        val sgAdapter = object : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_series_flag, seriesGroupOptions) {
            override fun convert(helper: BaseViewHolder, item: String) {
                val tvSeries = helper.getView<TextView>(R.id.tvSeriesFlag)
                tvSeries.text = item
                helper.getView<View>(R.id.tvSeriesFlagSelect).visibility = if (helper.layoutPosition == selectedSeriesGroupPosition) View.VISIBLE else View.GONE
                helper.itemView.nextFocusUpId = R.id.mGridViewFlag
                if (helper.layoutPosition == data.size - 1) {
                    helper.itemView.id = View.generateViewId()
                    helper.itemView.nextFocusRightId = helper.itemView.id
                } else {
                    helper.itemView.nextFocusRightId = View.NO_ID
                }
            }
        }
        seriesGroupAdapter = sgAdapter
        mSeriesGroupView?.adapter = sgAdapter

        llPlayerFragmentContainerBlock?.setOnClickListener {
            enterFullPreview()
            if (firstReverse) {
                jumpToPlay()
                firstReverse = false
            }
        }

        tvPlay?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            if (showPreview) {
                enterFullPreview()
                if (firstReverse) {
                    jumpToPlay()
                    firstReverse = false
                }
            } else {
                jumpToPlay()
            }
        }

        tvQuickSearch?.setOnClickListener {
            startQuickSearch()
            val quickSearchDialog = QuickSearchDialog(this@DetailActivity)
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData))
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord))
            quickSearchDialog.show()
            val pRunnable = pauseRunnable
            if (pRunnable != null && pRunnable.isNotEmpty()) {
                val pool = Executors.newFixedThreadPool(5)
                searchExecutorService = pool
                for (runnable in pRunnable) {
                    pool.execute(runnable)
                }
                pRunnable.clear()
                pauseRunnable = null
            }
            quickSearchDialog.setOnDismissListener {
                try {
                    val pool = searchExecutorService
                    if (pool != null) {
                        pauseRunnable = pool.shutdownNow()
                        searchExecutorService = null
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
            }
        }

        tvChangeSource?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            startDetailFallbackFromMenu()
        }

        tvCollect?.setOnClickListener {
            val text = tvCollect?.text?.toString() ?: ""
            val vInfo = vodInfo
            if (vInfo != null) {
                if ("加入收藏" == text) {
                    RoomDataManger.insertVodCollect(sourceKey, vInfo)
                    Toast.makeText(this@DetailActivity, "已加入收藏夹", Toast.LENGTH_SHORT).show()
                    tvCollect?.text = "取消收藏"
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vInfo)
                    Toast.makeText(this@DetailActivity, "已移除收藏夹", Toast.LENGTH_SHORT).show()
                    tvCollect?.text = "加入收藏"
                }
            }
        }

        tvPlayUrl?.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = tvPlayUrl?.text?.toString()?.replace("播放地址：", "") ?: ""
            cm?.setPrimaryClip(ClipData.newPlainText(null, text))
            Toast.makeText(this@DetailActivity, "已复制", Toast.LENGTH_SHORT).show()
        }

        tvSeriesSort?.setOnClickListener {
            val vInfo = vodInfo
            val sMap = vInfo?.seriesMap
            if (vInfo != null && sMap != null && sMap.isNotEmpty()) {
                vInfo.reverseSort = !vInfo.reverseSort
                isReverse = !isReverse
                tvSeriesSort?.text = if (isReverse) "倒序" else "正序"
                vInfo.reverse()
                val list = sMap[vInfo.playFlag]
                if (vInfo.playIndex >= 0 && list != null) {
                    vInfo.playIndex = (list.size - 1) - vInfo.playIndex
                }
                firstReverse = !firstReverse
                setSeriesGroupOptions()
                seriesAdapter?.notifyDataSetChanged()

                if (vInfo.playIndex >= 0) customSeriesScrollPos(vInfo.playIndex)
                val groupView = currentSeriesGroupView
                if (groupView != null) {
                    val txtView = groupView.findViewById<TextView>(R.id.tvSeriesFlag)
                    txtView?.setTextColor(Color.WHITE)
                }
            }
        }

        tvDesc?.setOnClickListener { v ->
            runOnUiThread {
                FastClickCheckUtil.check(v)
                val dialog = DescDialog(this)
                val description = StringBuilder(mVideo?.name ?: "")
                for (field in arrayOf(tvSite, tvYear, tvArea, tvLang, tvType, tvActor, tvDirector, tvDes, tvPlayUrl)) {
                    if (field != null && field.text.isNotEmpty()) {
                        description.append("\n\n").append(field.text)
                    }
                }
                dialog.setDescribe(description.toString())
                dialog.show()
            }
        }

        mGridView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                seriesSelect = false
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                seriesSelect = true
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {}
        })

        mGridViewFlag?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            private fun refresh(itemView: View, position: Int) {
                val newFlag = seriesFlagAdapter?.data?.getOrNull(position)?.name ?: return
                val vInfo = vodInfo ?: return
                val sMap = vInfo.seriesMap ?: return
                if (vInfo.playFlag != newFlag) {
                    val oldFlag = vInfo.playFlag
                    val oldIndex = vInfo.playIndex
                    var currentSeries = getPlayingSeries(previewVodInfo, previewVodInfo?.playFlag)
                    val oldSeriesList = sMap[oldFlag]
                    if (currentSeries == null && previewVodInfo == null && oldIndex >= 0 && !oldSeriesList.isNullOrEmpty()) {
                        val safeOldIndex = Math.max(0, Math.min(oldIndex, oldSeriesList.size - 1))
                        currentSeries = oldSeriesList[safeOldIndex]
                    }
                    if (currentSeries == null) {
                        currentSeries = routeSwitchSeries
                    }
                    val sFlags = vInfo.seriesFlags
                    if (sFlags != null) {
                        for (i in 0 until sFlags.size) {
                            val flag = sFlags[i]
                            if (flag.name == oldFlag) {
                                flag.selected = false
                                val oldItemView = mGridViewFlag?.layoutManager?.findViewByPosition(i)
                                oldItemView?.findViewById<View>(R.id.tvSeriesFlagSelect)?.visibility = View.GONE
                                break
                            }
                        }
                        val flag = sFlags.getOrNull(position)
                        if (flag != null) {
                            flag.selected = true
                            itemView.findViewById<View>(R.id.tvSeriesFlagSelect)?.visibility = View.VISIBLE
                        }
                    }
                    if (oldSeriesList != null && oldIndex >= 0 && oldSeriesList.size > oldIndex) {
                        oldSeriesList[oldIndex].selected = false
                    }
                    vInfo.playFlag = newFlag
                    val newSeriesList = sMap[newFlag]
                    if (!newSeriesList.isNullOrEmpty()) {
                        vInfo.playIndex = findMatchingEpisodeIndex(currentSeries, newSeriesList)
                        for (series in newSeriesList) {
                            series.selected = false
                        }
                        if (vInfo.playIndex >= 0) {
                            newSeriesList[vInfo.playIndex].selected = true
                            routeSwitchSeries = newSeriesList[vInfo.playIndex]
                        } else if (currentSeries != null) {
                            routeSwitchSeries = currentSeries
                        }
                    }
                    refreshList()
                }
                seriesFlagFocus = itemView
            }

            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (itemView != null) refresh(itemView, position)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (itemView != null) refresh(itemView, position)
            }
        })

        seriesAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val vInfo = vodInfo ?: return@OnItemClickListener
            val sMap = vInfo.seriesMap ?: return@OnItemClickListener
            val list = sMap[vInfo.playFlag] ?: return@OnItemClickListener
            if (list.isNotEmpty()) {
                var reload = false
                val sData = seriesAdapter?.data ?: ArrayList()
                for (j in 0 until list.size) {
                    if (j < sData.size) {
                        sData[j].selected = false
                        seriesAdapter?.notifyItemChanged(j)
                    }
                }
                if (vInfo.playIndex != position) {
                    if (position < sData.size) {
                        sData[position].selected = true
                        seriesAdapter?.notifyItemChanged(position)
                    }
                    vInfo.playIndex = position
                    if (position < sData.size) {
                        routeSwitchSeries = sData[position]
                    }
                    reload = true
                }
                if (preFlag.isNotEmpty() && vInfo.playFlag != preFlag) {
                    reload = true
                }
                val isCurrentPlaying = !showPreview || isCurrentPreviewPlaying(position)
                if (showPreview && !isCurrentPlaying) {
                    reload = true
                }
                if (vInfo.playIndex >= 0 && vInfo.playIndex < sData.size) {
                    sData[vInfo.playIndex].selected = true
                    seriesAdapter?.notifyItemChanged(vInfo.playIndex)
                }
                if (showPreview && !fullWindows && previewVodInfo != null && TextUtils.equals(vInfo.playFlag, previewVodInfo?.playFlag) && (playFragment?.player?.isPlaying() == true || isCurrentPlaying)) {
                    enterFullPreview()
                }
                if (!showPreview || reload) {
                    jumpToPlay()
                    firstReverse = false
                }
            }
        }

        mSeriesGroupView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                val txtView = itemView?.findViewById<TextView>(R.id.tvSeriesFlag)
                txtView?.setTextColor(Color.WHITE)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (itemView != null) selectSeriesGroup(itemView, position)
                val vInfo = vodInfo
                val sMap = vInfo?.seriesMap
                if (vInfo != null && sMap != null && (sMap[vInfo.playFlag]?.size ?: 0) > 0) {
                    val targetPos = position * GroupCount
                    customSeriesScrollPos(targetPos)
                }
                currentSeriesGroupView = itemView
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {}
        })

        tvSeriesSort?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val vInfo = vodInfo
                val sMap = vInfo?.seriesMap
                if (vInfo != null && sMap != null && (sMap[vInfo.playFlag]?.size ?: 0) > 0) {
                    val firstVisible = mGridView?.firstVisiblePosition ?: 0
                    val lastVisible = mGridView?.lastVisiblePosition ?: 0
                    if (vInfo.playIndex >= 0 && (vInfo.playIndex < firstVisible || vInfo.playIndex > lastVisible)) {
                        customSeriesScrollPos(vInfo.playIndex)
                    }
                }
            } else {
                tvSeriesSort?.setTextColor(Color.WHITE)
            }
        }

        seriesGroupAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            selectSeriesGroup(view, position)
            val vInfo = vodInfo
            val sMap = vInfo?.seriesMap
            if (vInfo != null && sMap != null && (sMap[vInfo.playFlag]?.size ?: 0) > 0) {
                val targetPos = position * GroupCount + 1
                customSeriesScrollPos(targetPos)
            }
            val groupView = currentSeriesGroupView
            if (groupView != null) {
                val txtView = groupView.findViewById<TextView>(R.id.tvSeriesFlag)
                txtView?.setTextColor(Color.WHITE)
            }
            currentSeriesGroupView = view
        }

        if (showPreview) {
            llPlayerFragmentContainerBlock?.requestFocus()
        } else {
            tvPlay?.requestFocus()
        }
        llLayout?.let { setLoadSir(it) }
        if (fullWindows) {
            setFullPreview(true)
        }
    }

    private fun selectSeriesGroup(selectedView: View, position: Int) {
        if (selectedSeriesGroupPosition == position) return
        val previousView = mSeriesGroupView?.layoutManager?.findViewByPosition(selectedSeriesGroupPosition)
        previousView?.findViewById<View>(R.id.tvSeriesFlagSelect)?.visibility = View.GONE
        selectedSeriesGroupPosition = position
        selectedView.findViewById<View>(R.id.tvSeriesFlagSelect)?.visibility = View.VISIBLE
    }

    fun customSeriesScrollPos(targetPos: Int) {
        if (targetPos < 0) return
        mGridViewLayoutMgr?.scrollToPositionWithOffset(if (targetPos > 10) targetPos - 10 else 0, 0)
        mGridView?.postDelayed({
            val scroller = smoothScroller
            if (scroller != null) {
                scroller.targetPosition = targetPos
                mGridViewLayoutMgr?.startSmoothScroll(scroller)
            }
            mGridView?.smoothScrollToPosition(targetPos)
        }, 50)
    }

    private fun initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch()
    }

    private var pauseRunnable: MutableList<Runnable>? = null

    private fun jumpToPlay() {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val list = sMap[vInfo.playFlag] ?: return
        if (list.isNotEmpty()) {
            preFlag = vInfo.playFlag ?: ""
            val safeIndex = Math.max(0, Math.min(vInfo.playIndex, list.size - 1))
            val series = list[safeIndex]
            setTextShow(tvPlayUrl, "播放地址：", series.url ?: "")
            val bundle = Bundle().apply {
                putString("sourceKey", sourceKey)
            }
            insertVod(firstsourceKey, vInfo)
            App.getInstance().vodInfo = vInfo
            if (showPreview) {
                ensurePlayFragment()
                updatePreviewVodInfo()
                App.getInstance().vodInfo = previewVodInfo
                playFragment?.setData(bundle)
            } else {
                ensurePlayFragment()
                playFragment?.setData(bundle)
                enterFullPreview()
            }
        }
    }

    private fun updatePreviewVodInfo() {
        val vInfo = vodInfo ?: return
        val pInfo = previewVodInfo ?: VodInfo().also { previewVodInfo = it }
        pInfo.id = vInfo.id
        pInfo.name = vInfo.name
        pInfo.pic = vInfo.pic
        pInfo.sourceKey = vInfo.sourceKey
        pInfo.playNote = vInfo.playNote
        pInfo.seriesFlags = vInfo.seriesFlags
        pInfo.seriesMap = vInfo.seriesMap
        pInfo.playerCfg = vInfo.playerCfg
        pInfo.playFlag = vInfo.playFlag
        pInfo.playIndex = vInfo.playIndex
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshList() {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val flagList = sMap[vInfo.playFlag] ?: return
        if (flagList.size <= vInfo.playIndex) {
            vInfo.playIndex = 0
        }

        var canSelect = true
        for (j in 0 until flagList.size) {
            if (flagList[j].selected) {
                canSelect = false
                break
            }
        }
        if (canSelect && vInfo.playIndex >= 0 && vInfo.playIndex < flagList.size) {
            flagList[vInfo.playIndex].selected = true
        }

        val pFont = Paint()
        val rect = Rect()
        val listSize = flagList.size
        var w = 1
        for (i in 0 until listSize) {
            val name = flagList[i].name ?: ""
            pFont.getTextBounds(name, 0, name.length, rect)
            if (w < rect.width()) {
                w = rect.width()
            }
        }
        w += 32
        val screenWidth = windowManager.defaultDisplay.width / 3
        var offset = screenWidth / w
        if (offset <= 2) offset = 2
        if (offset > 6) offset = 6
        mGridViewLayoutMgr?.spanCount = if (isPhonePortrait()) Math.min(3, offset) else offset
        seriesAdapter?.setNewData(flagList)

        setSeriesGroupOptions()

        mGridView?.postDelayed({
            if (vInfo.playIndex >= 0) customSeriesScrollPos(vInfo.playIndex)
        }, 100)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setSeriesGroupOptions() {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val list = sMap[vInfo.playFlag] ?: return
        val listSize = list.size
        val offset = mGridViewLayoutMgr?.spanCount ?: 3
        seriesGroupOptions.clear()
        GroupCount = if (offset == 3 || offset == 6) 30 else 20
        if (listSize in 101..400) GroupCount = 60
        if (listSize > 400) GroupCount = 120
        if (listSize > 1) {
            tvSeriesGroup?.visibility = View.VISIBLE
            val remainedOptionSize = listSize % GroupCount
            val optionSize = listSize / GroupCount

            for (i in 0 until optionSize) {
                if (vInfo.reverseSort) {
                    seriesGroupOptions.add(String.format(Locale.getDefault(), "%d - %d", listSize - (i * GroupCount + 1) + 1, listSize - (i * GroupCount + GroupCount) + 1))
                } else {
                    seriesGroupOptions.add(String.format(Locale.getDefault(), "%d - %d", i * GroupCount + 1, i * GroupCount + GroupCount))
                }
            }
            if (remainedOptionSize > 0) {
                if (vInfo.reverseSort) {
                    seriesGroupOptions.add(String.format(Locale.getDefault(), "%d - %d", listSize - (optionSize * GroupCount + 1) + 1, listSize - (optionSize * GroupCount + remainedOptionSize) + 1))
                } else {
                    seriesGroupOptions.add(String.format(Locale.getDefault(), "%d - %d", optionSize * GroupCount + 1, optionSize * GroupCount + remainedOptionSize))
                }
            }

            selectedSeriesGroupPosition = Math.max(0, Math.min(vInfo.playIndex / GroupCount, seriesGroupOptions.size - 1))
            seriesGroupAdapter?.notifyDataSetChanged()
        } else {
            tvSeriesGroup?.visibility = View.GONE
        }
        if (mGridViewFlag?.hasFocus() != true) seriesFlagAdapter?.notifyDataSetChanged()
        mGridViewQuality?.nextFocusUpId = if (tvSeriesGroup?.visibility == View.VISIBLE) R.id.mSeriesSortTv else R.id.mGridViewFlag
    }

    private fun updateQualityOptions(result: JSONObject?) {
        val options = ArrayList<String>()
        try {
            val value = result?.opt("url")
            val urls = if (value is JSONArray) value else if (value is String) JSONArray(value) else null
            if (urls != null) {
                var i = 0
                while (i + 1 < urls.length()) {
                    options.add(urls.optString(i))
                    i += 2
                }
            }
        } catch (th: Throwable) {
        }
        if (qualityOptions == options) {
            if (qualityPosition == 0) return
            qualityPosition = 0
            qualityAdapter?.notifyDataSetChanged()
            return
        }
        qualityOptions.clear()
        qualityOptions.addAll(options)
        qualityPosition = 0
        val visible = showPreview && options.size > 1
        mGridViewQuality?.visibility = if (visible) View.VISIBLE else View.GONE
        seriesFlagAdapter?.notifyDataSetChanged()
        seriesAdapter?.notifyDataSetChanged()
        qualityAdapter?.setNewData(ArrayList(qualityOptions))
        val up = if (tvSeriesGroup?.visibility == View.VISIBLE) R.id.mSeriesSortTv else R.id.mGridViewFlag
        val down = if (visible) R.id.mGridViewQuality else R.id.mGridView
        mGridViewQuality?.nextFocusUpId = up
        mGridViewQuality?.nextFocusDownId = R.id.mGridView
        tvSeriesSort?.nextFocusDownId = down
        mSeriesGroupView?.nextFocusDownId = down
    }

    private fun setTextShow(view: TextView?, tag: String, info: String?) {
        if (view == null) return
        if (info.isNullOrEmpty() || info.trim().isEmpty()) {
            view.visibility = View.GONE
            return
        }
        view.text = Html.fromHtml(getHtml(tag, info))
        val compact = isPhonePortrait() && (view == tvActor || view == tvDirector || view == tvDes || view == tvPlayUrl
                || view == tvArea || view == tvLang || view == tvType)
        view.visibility = if (compact) View.GONE else View.VISIBLE
    }

    private fun removeHtmlTag(info: String?): String {
        if (info.isNullOrEmpty()) return ""
        var text = info.replace(Regex("\\[a=cr:(?:\\{.*?\\}|\\[.*?\\])/](.*?)\\[/a]"), "$1")
        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            Html.fromHtml(text).toString()
        }
        return text.replace(Regex("\\s"), "")
    }

    private fun applyPreviewRoundCorners() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val radius = resources.getDimension(R.dimen.preview_player_radius)
        val provider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        llPlayerFragmentContainer?.clipToOutline = true
        llPlayerFragmentContainer?.outlineProvider = provider
        llPlayerFragmentContainerBlock?.clipToOutline = true
        llPlayerFragmentContainerBlock?.outlineProvider = provider
    }

    private fun applyThumbPreviewStyle() {
        thumbContainer?.visibility = if (showPreview) View.GONE else View.VISIBLE
        llPlayerPlace?.visibility = if (showPreview) View.VISIBLE else View.GONE
        ivThumb?.visibility = if (!showPreview) View.VISIBLE else View.GONE
        thumbContainer?.setBackgroundResource(if (showPreview) R.drawable.shape_detail_thumb_bg else R.drawable.shape_detail_thumb_idle_bg)
    }

    private fun setPreviewRoundClip(enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        llPlayerFragmentContainer?.clipToOutline = enable
        llPlayerFragmentContainerBlock?.clipToOutline = enable
        llPlayerFragmentContainer?.setBackgroundResource(if (enable) R.drawable.preview_player_round else android.R.color.black)
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel?.detailResult?.observe(this, Observer { absXml ->
            if (detailFallbackActive && !detailFallbackLoadingCandidate) {
                return@Observer
            }
            if (absXml != null && !TextUtils.isEmpty(absXml.sourceKey)
                && !TextUtils.equals(absXml.sourceKey, sourceKey)
                && !("push_fallback" == absXml.sourceKey && "push_agent" == sourceKey)
            ) {
                return@Observer
            }
            val videoList = absXml?.movie?.videoList
            if (absXml != null && videoList != null && videoList.isNotEmpty()) {
                val fallbackResult = detailFallbackLoadingCandidate
                if (detailFallbackLoadingCandidate) {
                    detailFallbackLoadingCandidate = false
                    llLayout?.removeCallbacks(detailFallbackDetailTimeout)
                }
                if (fallbackResult) {
                    val fallbackSource = ApiConfig.get().getSource(sourceKey)
                    val fallbackName = fallbackSource?.name ?: sourceKey
                    Toast.makeText(this@DetailActivity, "站点切换至$fallbackName", Toast.LENGTH_SHORT).show()
                }
                showSuccess()
                if (!TextUtils.isEmpty(absXml.msg) && absXml.msg != "数据列表") {
                    resetDetailFallback()
                    Toast.makeText(this@DetailActivity, absXml.msg, Toast.LENGTH_SHORT).show()
                    showEmpty()
                    return@Observer
                }
                val video = videoList[0]
                mVideo = video
                video.id = vodId
                if (TextUtils.isEmpty(video.name)) video.name = vod_name
                if (TextUtils.isEmpty(video.name)) video.name = "TVBox"
                val vInfo = VodInfo()
                vodInfo = vInfo
                routeSwitchSeries = null
                if (video.pic.isNullOrEmpty() && vod_picture.isNotEmpty()) {
                    video.pic = vod_picture
                }
                vInfo.setVideo(video)
                vInfo.sourceKey = video.sourceKey
                sourceKey = video.sourceKey ?: ""

                tvName?.text = video.name
                var displaySource = ApiConfig.get().getSource(firstsourceKey)
                if (displaySource == null) {
                    displaySource = ApiConfig.get().getSource(sourceKey)
                }
                setTextShow(tvSite, "来源：", displaySource?.name ?: "")
                setTextShow(tvYear, "年份：", if (video.year == 0) "" else video.year.toString())
                setTextShow(tvArea, "地区：", video.area)
                setTextShow(tvLang, "语言：", video.lang)
                if (firstsourceKey != sourceKey) {
                    val srcName = ApiConfig.get().getSource(sourceKey)?.name ?: ""
                    setTextShow(tvType, "类型：", "[$srcName] 解析")
                } else {
                    setTextShow(tvType, "类型：", video.type)
                }
                setTextShow(tvActor, "演员：", removeHtmlTag(video.actor))
                setTextShow(tvDirector, "导演：", removeHtmlTag(video.director))
                setTextShow(tvDes, "内容简介：", removeHtmlTag(video.des))
                val iv = ivThumb
                if (iv != null) {
                    if (!TextUtils.isEmpty(video.pic)) {
                        ImgUtil.load(
                            DefaultConfig.checkReplaceProxy(video.pic), iv,
                            AutoSizeUtils.mm2px(mContext, 10f),
                            AutoSizeUtils.mm2px(mContext, 300f),
                            AutoSizeUtils.mm2px(mContext, 400f),
                            video.name ?: ""
                        )
                    } else {
                        iv.setImageDrawable(ImgUtil.createTextDrawable(video.name ?: ""))
                    }
                }

                val sMap = vInfo.seriesMap
                if (sMap != null && sMap.isNotEmpty()) {
                    mGridViewFlag?.visibility = View.VISIBLE
                    mGridView?.visibility = View.VISIBLE
                    tvPlay?.visibility = View.VISIBLE
                    mEmptyPlayList?.visibility = View.GONE

                    val vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId)
                    if (vodInfoRecord != null) {
                        vInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0)
                        vInfo.playFlag = vodInfoRecord.playFlag
                        vInfo.playerCfg = vodInfoRecord.playerCfg
                        vInfo.reverseSort = vodInfoRecord.reverseSort
                    } else {
                        vInfo.playIndex = 0
                        vInfo.playFlag = null
                        vInfo.playerCfg = ""
                        vInfo.reverseSort = false
                    }

                    if (vInfo.reverseSort) {
                        vInfo.reverse()
                    }

                    if (vInfo.playFlag == null || !sMap.containsKey(vInfo.playFlag)) {
                        vInfo.playFlag = sMap.keys.firstOrNull()
                    }

                    restoreDetailFallbackEpisode()
                    resetDetailFallback()
                    val playingSeriesList = sMap[vInfo.playFlag] ?: ArrayList()
                    vInfo.playIndex = Math.max(0, Math.min(vInfo.playIndex, playingSeriesList.size - 1))

                    var flagScrollTo = 0
                    val sFlags = vInfo.seriesFlags
                    if (sFlags != null) {
                        for (j in 0 until sFlags.size) {
                            val flag = sFlags[j]
                            if (flag.name == vInfo.playFlag) {
                                flagScrollTo = j
                                flag.selected = true
                            } else {
                                flag.selected = false
                            }
                        }
                    }
                    if (playingSeriesList.isNotEmpty()) {
                        setTextShow(tvPlayUrl, "播放地址：", playingSeriesList[vInfo.playIndex].url)
                    }
                    seriesFlagAdapter?.setNewData(vInfo.seriesFlags)
                    mGridViewFlag?.scrollToPosition(flagScrollTo)

                    refreshList()
                    if (showPreview) {
                        jumpToPlay()
                        llPlayerFragmentContainer?.visibility = View.VISIBLE
                        llPlayerFragmentContainerBlock?.visibility = View.VISIBLE
                        toggleSubtitleTextSize()
                    }
                } else {
                    mGridViewFlag?.visibility = View.GONE
                    mGridView?.visibility = View.GONE
                    tvSeriesGroup?.visibility = View.GONE
                    tvPlay?.visibility = View.GONE
                    mEmptyPlayList?.visibility = View.VISIBLE
                    handleNoPlayableDetail()
                }
            } else {
                if (detailFallbackLoadingCandidate) {
                    detailFallbackLoadingCandidate = false
                    detailFallbackDetailTimedOut = true
                    llLayout?.removeCallbacks(detailFallbackDetailTimeout)
                    loadNextDetailFallbackSource()
                    return@Observer
                }
                handleEmptyDetail(absXml)
            }
        })
        sourceViewModel?.detailFallbackSearchResult?.observe(this, Observer { absXml ->
            onDetailFallbackSearchResult(absXml)
        })
    }

    private fun getHtml(label: String, content: String?): String {
        val c = content ?: ""
        return "$label<font color=\"#FFFFFF\">$c</font>"
    }

    private var vod_picture = ""
    private var vod_name = ""

    private fun initData() {
        val intent = intent
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            vod_name = bundle?.getString("title", "") ?: ""
            vod_picture = bundle?.getString("picture", "") ?: ""
            fromCollect = bundle?.getBoolean("collect", false) ?: false
            val fallbackCandidates = bundle?.getSerializable(EXTRA_DETAIL_FALLBACK_CANDIDATES)
            if (fallbackCandidates is ArrayList<*>) {
                @Suppress("UNCHECKED_CAST")
                cacheDetailFallbackCandidates(vod_name, fallbackCandidates as ArrayList<Movie.Video>)
            }
            loadDetail(bundle?.getString("id", null), bundle?.getString("sourceKey", "") ?: "")
        }
    }

    private fun loadDetail(vid: String?, key: String) {
        loadDetail(vid, key, false)
    }

    private fun loadDetail(vid: String?, key: String, fallback: Boolean) {
        if (!fallback) {
            resetDetailFallback()
        }
        vodId = vid
        sourceKey = key
        firstsourceKey = key
        if (vid.isNullOrEmpty() || vid.startsWith("msearch:") || ApiConfig.get().getSource(sourceKey) == null) {
            handleNoPlayableDetail()
            return
        }
        if (!fallback) {
            showLoading()
        }
        if (fallback && detailFallbackActive) {
            llLayout?.removeCallbacks(detailFallbackDetailTimeout)
            llLayout?.postDelayed(detailFallbackDetailTimeout, DETAIL_FALLBACK_DETAIL_TIMEOUT_MS)
        }
        sourceViewModel?.getDetail(sourceKey, vodId ?: "", fallback && detailFallbackActive)
        val isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId)
        if (isVodCollect) {
            tvCollect?.text = "取消收藏"
        } else {
            tvCollect?.text = "加入收藏"
        }
    }

    private fun handleEmptyDetail(data: AbsXml?) {
        val shouldFinish = data != null && !TextUtils.isEmpty(data.msg)
        if (shouldFinish || fromCollect) {
            resetDetailFallback()
            if (shouldFinish) {
                Toast.makeText(this, data?.msg ?: "", Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }
        handleNoPlayableDetail()
    }

    private fun handleNoPlayableDetail() {
        if (detailFallbackActive) {
            detailFallbackLoadingCandidate = false
            loadNextDetailFallbackSource()
            return
        }
        startDetailFallback()
    }

    fun startDetailFallbackAfterLinesExhausted(): Boolean {
        return startDetailFallback(false)
    }

    private fun startDetailFallbackFromMenu() {
        if (!startDetailFallback(true)) {
            Toast.makeText(this, "暂无可切换的片源", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDetailFallback(manual: Boolean): Boolean {
        val currentSource = ApiConfig.get().getSource(sourceKey)
        if (isFinishing || currentSource == null || !currentSource.isChangeable()) {
            return false
        }
        if (detailFallbackActive) {
            return true
        }
        val sMap = vodInfo?.seriesMap
        detailFallbackKeepCurrentDetail = mVideo != null && vodInfo != null && sMap != null && sMap.isNotEmpty()
        llLayout?.removeCallbacks(detailFallbackDetailTimeout)
        captureDetailFallbackEpisode()
        val video = mVideo
        if (video != null && !video.name.isNullOrEmpty()) {
            vod_name = video.name ?: ""
        }
        if (vod_name.isEmpty()) {
            return false
        }
        detailFallbackExcludedSourceKey = sourceKey
        detailFallbackTitle = vod_name.trim()
        addDetailFallbackUsedSource(sourceKey)
        if (loadDetailFallbackCache()) {
            return true
        }
        LOG.i("echo-detail fallback " + (if (manual) "manual" else "after lines exhausted") + ": " + vod_name)
        startDetailFallback()
        return detailFallbackActive
    }

    private fun startDetailFallback() {
        detailFallbackTitle = vod_name.trim()
        if (detailFallbackTitle.isEmpty()) {
            showDetailEmpty()
            return
        }

        for (bean in ApiConfig.get().getSourceBeanList()) {
            if (bean.isSearchable() && bean.isChangeable() && !TextUtils.equals(bean.key, detailFallbackExcludedSourceKey) && !isDetailFallbackSourceUsed(bean.key)) {
                detailFallbackSourceOrder.add(bean.key)
            }
        }
        if (detailFallbackSourceOrder.isEmpty()) {
            if (!detailFallbackKeepCurrentDetail) {
                showDetailEmpty()
            }
            return
        }

        detailFallbackActive = true
        detailFallbackSearching = true
        detailFallbackSearchCollecting = true
        detailFallbackSearchTimedOut = false
        detailFallbackDetailTimedOut = false
        detailFallbackSearchTimeoutScheduled = false
        detailFallbackLoadingCandidate = false
        detailFallbackBatchIndex = 0
        detailFallbackNextSourceIndex = 0
        detailFallbackToken = "detail_fallback_" + (++detailFallbackRequestIndex)
        detailFallbackTriedKeys.add(getDetailFallbackKey(sourceKey, vodId))
        LOG.i("echo-detail fallback search: " + detailFallbackTitle + ", sources=" + detailFallbackSourceOrder.size)
        scheduleDetailFallbackSearch()
    }

    private fun captureDetailFallbackEpisode() {
        detailFallbackEpisode = null
        detailFallbackEpisodeIndex = -1
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        if (vInfo.playFlag.isNullOrEmpty()) return
        val seriesList = sMap[vInfo.playFlag]
        if (seriesList.isNullOrEmpty()) return
        detailFallbackEpisodeIndex = Math.max(0, Math.min(vInfo.playIndex, seriesList.size - 1))
        detailFallbackEpisode = seriesList[detailFallbackEpisodeIndex]
    }

    private fun restoreDetailFallbackEpisode() {
        val targetEpisode = detailFallbackEpisode ?: return
        if (detailFallbackEpisodeIndex < 0) return
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val preferredFlag = vInfo.playFlag
        val preferredList = sMap[preferredFlag]
        var matchedIndex = findMatchingEpisodeIndex(targetEpisode, preferredList)
        if (matchedIndex >= 0) {
            vInfo.playIndex = matchedIndex
            return
        }
        val sFlags = vInfo.seriesFlags
        if (sFlags != null) {
            for (seriesFlag in sFlags) {
                if (seriesFlag == null || TextUtils.isEmpty(seriesFlag.name) || TextUtils.equals(preferredFlag, seriesFlag.name)) {
                    continue
                }
                val seriesList = sMap[seriesFlag.name]
                matchedIndex = findMatchingEpisodeIndex(targetEpisode, seriesList)
                if (matchedIndex >= 0) {
                    vInfo.playFlag = seriesFlag.name
                    vInfo.playIndex = matchedIndex
                    return
                }
            }
        }
        for (flag in sMap.keys) {
            if (TextUtils.equals(preferredFlag, flag) || containsSeriesFlag(flag)) {
                continue
            }
            val seriesList = sMap[flag]
            matchedIndex = findMatchingEpisodeIndex(targetEpisode, seriesList)
            if (matchedIndex >= 0) {
                vInfo.playFlag = flag
                vInfo.playIndex = matchedIndex
                return
            }
        }
        if (!preferredList.isNullOrEmpty()) {
            vInfo.playIndex = Math.max(0, Math.min(detailFallbackEpisodeIndex, preferredList.size - 1))
        }
    }

    private fun containsSeriesFlag(name: String): Boolean {
        val flags = vodInfo?.seriesFlags ?: return false
        for (flag in flags) {
            if (flag != null && TextUtils.equals(flag.name, name)) {
                return true
            }
        }
        return false
    }

    private fun onDetailFallbackSearchResult(data: AbsXml?) {
        if (!detailFallbackActive || !detailFallbackSearchCollecting || data == null || detailFallbackBatchToken != data.searchToken) {
            return
        }
        detailFallbackPendingSources.remove(data.sourceKey)
        val videoList = data.movie?.videoList
        if (videoList != null) {
            for (video in videoList) {
                if (video == null || TextUtils.isEmpty(video.id) || isDetailFallbackSourceUsed(video.sourceKey) || detailFallbackTitle != (video.name?.trim() ?: "")) {
                    continue
                }
                val candidateKey = getDetailFallbackKey(video.sourceKey, video.id)
                if (!detailFallbackTriedKeys.contains(candidateKey) && detailFallbackCandidateKeys.add(candidateKey)) {
                    detailFallbackCandidates.add(video)
                    cacheDetailFallbackCandidate(video)
                }
            }
        }
        if (!detailFallbackLoadingCandidate && detailFallbackCandidates.isNotEmpty()
            && (!detailFallbackSearchTimedOut || detailFallbackDetailTimedOut)
        ) {
            LOG.i("echo-detail fallback candidates: " + detailFallbackCandidates.size)
            loadNextDetailFallbackSource()
        }
        if (!detailFallbackLoadingCandidate) {
            if (detailFallbackSearching) {
                scheduleDetailFallbackSearch()
            } else if (detailFallbackPendingSources.isEmpty()) {
                finishDetailFallbackSearchCollection()
            }
        }
    }

    private fun scheduleDetailFallbackSearch() {
        if (!detailFallbackActive || !detailFallbackSearching) {
            return
        }
        if (detailFallbackPendingSources.isNotEmpty() || detailFallbackLoadingCandidate) {
            return
        }
        if (detailFallbackNextSourceIndex >= detailFallbackSourceOrder.size) {
            detailFallbackSearching = false
            llLayout?.removeCallbacks(detailFallbackTimeout)
            stopDetailFallbackSearchExecutor()
            LOG.i("echo-detail fallback candidates: " + detailFallbackCandidates.size)
            loadNextDetailFallbackSource()
            return
        }

        stopDetailFallbackSearchExecutor()
        val pool = Executors.newFixedThreadPool(DETAIL_FALLBACK_MAX_SEARCH)
        detailFallbackSearchExecutor = pool
        detailFallbackBatchToken = detailFallbackToken + "_batch_" + (++detailFallbackBatchIndex)
        val batchEnd = Math.min(detailFallbackNextSourceIndex + DETAIL_FALLBACK_MAX_SEARCH, detailFallbackSourceOrder.size)
        while (detailFallbackNextSourceIndex < batchEnd) {
            val searchKey = detailFallbackSourceOrder[detailFallbackNextSourceIndex++]
            val searchTitle = detailFallbackTitle
            val searchToken = detailFallbackBatchToken
            detailFallbackPendingSources.add(searchKey)
            pool.execute {
                sourceViewModel?.getDetailFallbackSearch(searchKey, searchTitle, searchToken)
            }
        }
        if (!detailFallbackSearchTimeoutScheduled) {
            detailFallbackSearchTimeoutScheduled = true
            llLayout?.postDelayed(detailFallbackTimeout, DETAIL_FALLBACK_SEARCH_TIMEOUT_MS)
        }
    }

    private fun finishDetailFallbackSearchOnTimeout() {
        if (!detailFallbackActive || !detailFallbackSearching) {
            return
        }
        detailFallbackSearchTimeoutScheduled = false
        LOG.i("echo-detail fallback search timeout: " + detailFallbackBatchToken)
        detailFallbackSearching = false
        detailFallbackSearchTimedOut = true
        detailFallbackNextSourceIndex = detailFallbackSourceOrder.size
        if (!detailFallbackLoadingCandidate) {
            LOG.i("echo-detail fallback candidates: " + detailFallbackCandidates.size)
            if (detailFallbackCandidates.isNotEmpty()) {
                loadNextDetailFallbackSource()
            } else {
                showDetailFallbackEmptyIfNeeded()
            }
        }
    }

    private fun loadNextDetailFallbackSource() {
        while (detailFallbackCandidates.isNotEmpty()) {
            val video = detailFallbackCandidates.removeAt(0)
            val candidateKey = getDetailFallbackKey(video.sourceKey, video.id)
            if (isDetailFallbackSourceUsed(video.sourceKey) || !detailFallbackTriedKeys.add(candidateKey)) {
                continue
            }
            LOG.i("echo-detail fallback source: " + video.sourceKey + ", id=" + video.id)
            detailFallbackLoadingCandidate = true
            detailFallbackDetailTimedOut = false
            addDetailFallbackUsedSource(video.sourceKey ?: "")
            vod_name = video.name ?: ""
            vod_picture = video.pic ?: ""
            loadDetail(video.id, video.sourceKey ?: "", true)
            return
        }
        if (detailFallbackSearching || detailFallbackSearchCollecting) {
            if (detailFallbackPendingSources.isEmpty()) {
                if (detailFallbackSearching) {
                    scheduleDetailFallbackSearch()
                } else {
                    finishDetailFallbackSearchCollection()
                }
            }
            return
        }
        finishDetailFallbackWithoutResult()
    }

    private fun finishDetailFallbackSearchCollection() {
        if (!detailFallbackSearchCollecting) {
            return
        }
        detailFallbackSearchCollecting = false
        stopDetailFallbackSearchExecutor()
        OkGo.getInstance().cancelTag(DETAIL_FALLBACK_SEARCH_TAG)
        if (!detailFallbackLoadingCandidate) {
            finishDetailFallbackWithoutResult()
        }
    }

    private fun showDetailFallbackEmptyIfNeeded() {
        if (!detailFallbackKeepCurrentDetail) {
            showDetailEmpty()
        }
    }

    private fun finishDetailFallbackWithoutResult() {
        val keepCurrentDetail = detailFallbackKeepCurrentDetail
        resetDetailFallback()
        if (!keepCurrentDetail) {
            showDetailEmpty()
        }
    }

    private fun finishDetailFallbackDetailOnTimeout() {
        if (!detailFallbackActive || !detailFallbackLoadingCandidate) {
            return
        }
        LOG.i("echo-detail fallback detail timeout: " + sourceKey)
        detailFallbackLoadingCandidate = false
        detailFallbackDetailTimedOut = true
        OkGo.getInstance().cancelTag("detail")
        loadNextDetailFallbackSource()
    }

    private fun getDetailFallbackKey(key: String?, id: String?): String {
        return (key ?: "") + "|" + (id ?: "")
    }

    private fun loadDetailFallbackCache(): Boolean {
        val cachedCandidates = detailFallbackCache[detailFallbackTitle]
        if (cachedCandidates.isNullOrEmpty()) {
            return false
        }
        for (video in cachedCandidates) {
            val source = if (video != null) ApiConfig.get().getSource(video.sourceKey) else null
            if (video == null || source == null || !source.isChangeable()
                || TextUtils.equals(video.sourceKey, detailFallbackExcludedSourceKey) || isDetailFallbackSourceUsed(video.sourceKey)
            ) {
                continue
            }
            val candidateKey = getDetailFallbackKey(video.sourceKey, video.id)
            if (detailFallbackCandidateKeys.add(candidateKey)) {
                detailFallbackCandidates.add(video)
            }
        }
        if (detailFallbackCandidates.isEmpty()) {
            return false
        }
        detailFallbackActive = true
        detailFallbackLoadingCandidate = false
        detailFallbackTriedKeys.add(getDetailFallbackKey(sourceKey, vodId))
        LOG.i("echo-detail fallback cache: " + detailFallbackTitle + ", candidates=" + detailFallbackCandidates.size)
        loadNextDetailFallbackSource()
        return true
    }

    private fun cacheDetailFallbackCandidates(title: String?, candidates: List<Movie.Video>?) {
        val t = title?.trim() ?: ""
        if (t.isEmpty() || candidates.isNullOrEmpty()) {
            return
        }
        var cachedCandidates = detailFallbackCache[t]
        if (cachedCandidates == null) {
            cachedCandidates = ArrayList()
            detailFallbackCache[t] = cachedCandidates
        }
        for (video in candidates) {
            if (cachedCandidates.size >= DETAIL_FALLBACK_MAX_SEARCH || video == null
                || TextUtils.isEmpty(video.id) || t != (video.name?.trim() ?: "")
            ) {
                continue
            }
            val candidateKey = getDetailFallbackKey(video.sourceKey, video.id)
            var exists = false
            for (cachedVideo in cachedCandidates) {
                if (candidateKey == getDetailFallbackKey(cachedVideo.sourceKey, cachedVideo.id)) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                cachedCandidates.add(video)
            }
        }
    }

    private fun cacheDetailFallbackCandidate(video: Movie.Video) {
        var cachedCandidates = detailFallbackCache[detailFallbackTitle]
        if (cachedCandidates == null) {
            cachedCandidates = ArrayList()
            detailFallbackCache[detailFallbackTitle] = cachedCandidates
        }
        val candidateKey = getDetailFallbackKey(video.sourceKey, video.id)
        for (cachedVideo in cachedCandidates) {
            if (candidateKey == getDetailFallbackKey(cachedVideo.sourceKey, cachedVideo.id)) {
                return
            }
        }
        cachedCandidates.add(video)
    }

    private fun addDetailFallbackUsedSource(sourceKey: String?) {
        if (detailFallbackTitle.isEmpty() || sourceKey.isNullOrEmpty()) {
            return
        }
        var usedSources = detailFallbackUsedSourceKeys[detailFallbackTitle]
        if (usedSources == null) {
            usedSources = HashSet()
            detailFallbackUsedSourceKeys[detailFallbackTitle] = usedSources
        }
        usedSources.add(sourceKey)
    }

    private fun isDetailFallbackSourceUsed(sourceKey: String?): Boolean {
        if (sourceKey == null) return false
        val usedSources = detailFallbackUsedSourceKeys[detailFallbackTitle]
        return usedSources != null && usedSources.contains(sourceKey)
    }

    private fun showDetailEmpty() {
        showEmpty()
        llPlayerFragmentContainer?.visibility = View.GONE
        llPlayerFragmentContainerBlock?.visibility = View.GONE
    }

    private fun resetDetailFallback() {
        detailFallbackActive = false
        detailFallbackSearching = false
        detailFallbackSearchCollecting = false
        detailFallbackSearchTimedOut = false
        detailFallbackDetailTimedOut = false
        detailFallbackSearchTimeoutScheduled = false
        detailFallbackKeepCurrentDetail = false
        detailFallbackLoadingCandidate = false
        detailFallbackBatchIndex = 0
        detailFallbackNextSourceIndex = 0
        detailFallbackToken = ""
        detailFallbackBatchToken = ""
        detailFallbackTitle = ""
        detailFallbackExcludedSourceKey = ""
        detailFallbackSourceOrder.clear()
        detailFallbackCandidates.clear()
        detailFallbackPendingSources.clear()
        detailFallbackCandidateKeys.clear()
        detailFallbackTriedKeys.clear()
        detailFallbackEpisode = null
        detailFallbackEpisodeIndex = -1
        val layout = llLayout
        if (layout != null) {
            layout.removeCallbacks(detailFallbackTimeout)
            layout.removeCallbacks(detailFallbackDetailTimeout)
        }
        stopDetailFallbackSearchExecutor()
        OkGo.getInstance().cancelTag(DETAIL_FALLBACK_SEARCH_TAG)
    }

    private fun stopDetailFallbackSearchExecutor() {
        val executor = detailFallbackSearchExecutor
        if (executor != null) {
            executor.shutdownNow()
            detailFallbackSearchExecutor = null
        }
    }

    private var isFirstLoad = true

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            val obj = event.obj
            if (obj != null) {
                if (obj is VodInfo) {
                    syncPlayingVodInfo(obj)
                } else if (obj is Int) {
                    val index = obj
                    val vInfo = vodInfo
                    val sMap = vInfo?.seriesMap
                    val list = sMap?.get(vInfo?.playFlag)
                    val sData = seriesAdapter?.data ?: ArrayList()
                    if (list != null) {
                        for (j in 0 until list.size) {
                            if (j < sData.size) {
                                sData[j].selected = false
                                seriesAdapter?.notifyItemChanged(j)
                            }
                        }
                    }
                    if (index < sData.size) {
                        sData[index].selected = true
                        seriesAdapter?.notifyItemChanged(index)
                    }
                    if (!isFirstLoad) mGridView?.setSelection(index)
                    if (vInfo != null) {
                        vInfo.playIndex = index
                    }
                    if (index < sData.size) {
                        routeSwitchSeries = sData[index]
                    }
                    if (vInfo != null) {
                        insertVod(firstsourceKey, vInfo)
                    }
                    isFirstLoad = false
                } else if (obj is JSONObject) {
                    val vInfo = vodInfo
                    if (vInfo != null) {
                        vInfo.playerCfg = obj.toString()
                        insertVod(firstsourceKey, vInfo)
                    }
                } else if (obj is String) {
                    setTvPlayUrl(obj)
                }
            }
        } else if (event.type == RefreshEvent.TYPE_PLAY_QUALITY) {
            updateQualityOptions(event.obj as? JSONObject)
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            val video = event.obj as? Movie.Video
            if (video != null) {
                vod_name = video.name ?: ""
                vod_picture = video.pic ?: ""
                loadDetail(video.id, video.sourceKey ?: "")
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            val word = event.obj as? String
            if (word != null) {
                switchSearchWord(word)
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj as? AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    private var searchTitle = ""
    private var hadQuickStart = false
    private val quickSearchData = ArrayList<Movie.Video>()
    private val quickSearchWord = ArrayList<String>()
    private var searchExecutorService: ExecutorService? = null
    private var detailFallbackSearchExecutor: ExecutorService? = null
    private val detailFallbackSourceOrder = ArrayList<String>()
    private val detailFallbackCandidates = ArrayList<Movie.Video>()
    private val detailFallbackCache = HashMap<String, MutableList<Movie.Video>>()
    private val detailFallbackUsedSourceKeys = HashMap<String, MutableSet<String>>()
    private val detailFallbackPendingSources = HashSet<String>()
    private val detailFallbackCandidateKeys = HashSet<String>()
    private val detailFallbackTriedKeys = HashSet<String>()
    private var detailFallbackEpisode: VodInfo.VodSeries? = null
    private var detailFallbackEpisodeIndex = -1
    private var detailFallbackActive = false
    private var detailFallbackSearching = false
    private var detailFallbackSearchCollecting = false
    private var detailFallbackSearchTimedOut = false
    private var detailFallbackDetailTimedOut = false
    private var detailFallbackSearchTimeoutScheduled = false
    private var detailFallbackKeepCurrentDetail = false
    private var detailFallbackLoadingCandidate = false
    private var detailFallbackRequestIndex = 0
    private var detailFallbackBatchIndex = 0
    private var detailFallbackNextSourceIndex = 0
    private var detailFallbackToken = ""
    private var detailFallbackBatchToken = ""
    private var detailFallbackTitle = ""
    private var detailFallbackExcludedSourceKey = ""

    private val detailFallbackTimeout = Runnable {
        finishDetailFallbackSearchOnTimeout()
    }
    private val detailFallbackDetailTimeout = Runnable {
        finishDetailFallbackDetailOnTimeout()
    }

    private fun switchSearchWord(word: String) {
        OkGo.getInstance().cancelTag("quick_search")
        quickSearchData.clear()
        searchTitle = word
        searchResult()
    }

    private fun startQuickSearch() {
        initCheckedSourcesForSearch()
        if (hadQuickStart) return
        hadQuickStart = true
        OkGo.getInstance().cancelTag("quick_search")
        quickSearchWord.clear()
        searchTitle = mVideo?.name ?: ""
        quickSearchData.clear()
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle))
        searchResult()
    }

    private fun searchResult() {
        try {
            val pool = searchExecutorService
            if (pool != null) {
                pool.shutdownNow()
                searchExecutorService = null
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        val pool = Executors.newFixedThreadPool(5)
        searchExecutorService = pool
        val searchRequestList = ArrayList<SourceBean>()
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList())
        val home = ApiConfig.get().getHomeSourceBean()
        if (home != null) {
            searchRequestList.remove(home)
            searchRequestList.add(0, home)
        }

        val siteKey = ArrayList<String>()
        for (bean in searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue
            }
            val checkSources = mCheckSources
            if (checkSources != null && !checkSources.containsKey(bean.key)) {
                continue
            }
            siteKey.add(bean.key)
        }
        for (key in siteKey) {
            pool.execute {
                sourceViewModel?.getQuickSearch(key, searchTitle)
            }
        }
    }

    private fun searchData(absXml: AbsXml?) {
        val videoList = absXml?.movie?.videoList
        if (absXml != null && videoList != null && videoList.isNotEmpty()) {
            val data = ArrayList<Movie.Video>()
            for (video in videoList) {
                if (video.sourceKey == sourceKey && video.id == vodId) continue
                data.add(video)
            }
            quickSearchData.addAll(data)
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data))
        }
    }

    private fun syncPlayingVodInfo(playingVodInfo: VodInfo?) {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val newFlag = playingVodInfo?.playFlag ?: return
        if (newFlag.isEmpty() || !sMap.containsKey(newFlag)) {
            return
        }
        val newSeriesList = sMap[newFlag]
        if (newSeriesList.isNullOrEmpty()) {
            return
        }

        val oldFlag = vInfo.playFlag
        val oldIndex = vInfo.playIndex
        val sameFlag = TextUtils.equals(oldFlag, newFlag)
        val playingSeries = getPlayingSeries(playingVodInfo, newFlag)
        val newIndex = findMatchingEpisodeIndex(playingSeries, newSeriesList)
        vInfo.playFlag = newFlag
        vInfo.playIndex = newIndex
        if (playingVodInfo.playerCfg != null) {
            vInfo.playerCfg = playingVodInfo.playerCfg
        }

        val sFlags = vInfo.seriesFlags
        if (sFlags != null) {
            for (flag in sFlags) {
                flag.selected = flag.name == newFlag
            }
        }
        for (seriesList in sMap.values) {
            if (seriesList == null) continue
            for (series in seriesList) {
                series.selected = false
            }
        }
        if (newIndex >= 0 && newIndex < newSeriesList.size) {
            newSeriesList[newIndex].selected = true
            routeSwitchSeries = newSeriesList[newIndex]
        }

        seriesFlagAdapter?.notifyDataSetChanged()
        val sAdapter = seriesAdapter
        if (sameFlag && newIndex >= 0 && oldIndex >= 0 && oldIndex < newSeriesList.size && sAdapter != null) {
            if (oldIndex != newIndex) {
                sAdapter.notifyItemChanged(oldIndex)
                sAdapter.notifyItemChanged(newIndex)
            }
        } else {
            refreshList()
        }
        if (newIndex >= 0 && newIndex < newSeriesList.size) {
            setTvPlayUrl(newSeriesList[newIndex].url ?: "")
        }

        var flagIndex = -1
        if (sFlags != null) {
            for (i in 0 until sFlags.size) {
                if (sFlags[i].name == newFlag) {
                    flagIndex = i
                    break
                }
            }
        }
        if (flagIndex >= 0) {
            mGridViewFlag?.scrollToPosition(flagIndex)
            if (mGridViewFlag?.hasFocus() == true) {
                mGridViewFlag?.setSelection(flagIndex)
            }
        }
        if (!isFirstLoad && newIndex >= 0) {
            mGridView?.setSelection(newIndex)
        }

        insertVod(firstsourceKey, vInfo)
        isFirstLoad = false
    }

    private fun getPlayingSeries(playingVodInfo: VodInfo?, flag: String?): VodInfo.VodSeries? {
        if (playingVodInfo == null || playingVodInfo.seriesMap == null || flag.isNullOrEmpty()) {
            return null
        }
        val playingList = playingVodInfo.seriesMap?.get(flag)
        if (playingList.isNullOrEmpty()) {
            return null
        }
        val safeIndex = Math.max(0, Math.min(playingVodInfo.playIndex, playingList.size - 1))
        return playingList[safeIndex]
    }

    private fun isCurrentPreviewPlaying(position: Int): Boolean {
        val pInfo = previewVodInfo ?: return false
        val vInfo = vodInfo ?: return false
        val sMap = vInfo.seriesMap ?: return false
        val flag = vInfo.playFlag
        if (flag.isNullOrEmpty()) return false
        if (!TextUtils.equals(vInfo.playFlag, pInfo.playFlag) || pInfo.playIndex != position) {
            return false
        }
        val currentList = sMap[vInfo.playFlag]
        if (currentList == null || position < 0 || position >= currentList.size) {
            return false
        }
        val currentSeries = currentList[position]
        val previewSeries = getPlayingSeries(pInfo, pInfo.playFlag)
        return currentSeries != null && previewSeries != null && TextUtils.equals(currentSeries.url, previewSeries.url)
    }

    private fun findMatchingEpisodeIndex(currentSeries: VodInfo.VodSeries?, targetList: List<VodInfo.VodSeries>?): Int {
        if (targetList.isNullOrEmpty()) {
            return -1
        }
        if (targetList.size == 1) {
            return 0
        }
        if (currentSeries == null || currentSeries.name.isNullOrEmpty()) {
            return -1
        }
        val cName = currentSeries.name ?: ""
        val currentEpisode = extractEpisodeNumber(cName)
        var matchedIndex = -1
        var bestScore = 0
        for (i in 0 until targetList.size) {
            val targetSeries = targetList[i]
            val score = getEpisodeMatchScore(cName, currentEpisode, targetSeries.name)
            if (score > bestScore) {
                bestScore = score
                matchedIndex = i
            }
        }
        return matchedIndex
    }

    private fun getEpisodeMatchScore(currentName: String?, currentEpisode: Int, targetName: String?): Int {
        if (currentName.isNullOrEmpty() || targetName.isNullOrEmpty()) {
            return 0
        }
        if (targetName.equals(currentName, ignoreCase = true)) {
            return 100
        }
        if (currentEpisode >= 0 && extractEpisodeNumber(targetName) == currentEpisode) {
            return 80
        }
        val currentLower = currentName.toLowerCase(Locale.ROOT)
        val targetLower = targetName.toLowerCase(Locale.ROOT)
        if (currentEpisode < 0 && currentName.length >= 2 && targetLower.contains(currentLower)) {
            return 70
        }
        if (currentEpisode < 0 && targetName.length >= 2 && currentLower.contains(targetLower)) {
            return 60
        }
        return 0
    }

    private fun extractEpisodeNumber(name: String?): Int {
        if (name.isNullOrEmpty()) {
            return -1
        }
        try {
            var text = name.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            text = text.replace(Regex("\\b(19|20)\\d{2}\\b"), "")
            text = text.toLowerCase(Locale.ROOT).replace(Regex("2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4"), "")
            val matcher = Pattern.compile("(?i)(?:ep|第|e|[\\-\\.\\s])\\s?(\\d{1,4})").matcher(text)
            if (matcher.find()) {
                val group = matcher.group(1)
                if (group != null) return group.toInt()
            }
            val number = text.replace(Regex("\\D+"), "")
            if (number.isNotEmpty()) {
                return number.toInt()
            }
        } catch (ignored: Exception) {
        }
        return -1
    }

    private fun insertVod(sourceKey: String, vodInfo: VodInfo) {
        try {
            val seriesList = vodInfo.seriesMap?.get(vodInfo.playFlag)
            vodInfo.playNote = seriesList?.get(vodInfo.playIndex)?.name ?: ""
        } catch (th: Throwable) {
            vodInfo.playNote = ""
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo)
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
    }

    override fun onDestroy() {
        resetDetailFallback()
        super.onDestroy()
        try {
            val pool = searchExecutorService
            if (pool != null) {
                pool.shutdownNow()
                searchExecutorService = null
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        OkGo.getInstance().cancelTag("fenci")
        OkGo.getInstance().cancelTag("detail")
        OkGo.getInstance().cancelTag("quick_search")
        releasePlayFragment()
        EventBus.getDefault().unregister(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FULL_WINDOWS, fullWindows)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        val pf = playFragment
        if (fullWindows) {
            if (pf != null && pf.onBackPressed()) return
            exitFullPreview()
            return
        }
        if (!isPhonePortrait() && mGridView?.hasFocus() == true
            && mGridViewFlag?.visibility == View.VISIBLE
        ) {
            try {
                val focus = seriesFlagFocus
                if (focus != null && focus.isShown && focus.requestFocus()) {
                    return
                }
                if (mGridViewFlag?.requestFocus() == true) {
                    return
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        if (showPreview && pf != null) {
            try {
                pf.setPlayTitle(false)
                pf.setExitingPreview(true)
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!fullWindows && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode == KeyEvent.KEYCODE_MENU) {
            startDetailFallbackFromMenu()
            return true
        }
        val pf = playFragment
        if (pf != null && fullWindows) {
            if (pf.dispatchKeyEvent(event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val pf = playFragment
        if (event != null && pf != null && fullWindows) {
            if (pf.onKeyDown(keyCode, event)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val pf = playFragment
        if (event != null && pf != null && fullWindows) {
            if (pf.onKeyUp(keyCode, event)) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    var previewVodInfo: VodInfo? = null
    var fullWindows: Boolean = false
    private var previewOrientation: Int = 0
    private var previewOrientationChanged: Boolean = false
    var windowsPreview: ViewGroup.LayoutParams? = null
    var windowsFull: ViewGroup.LayoutParams? = null

    fun toggleFullPreview() {
        setFullPreview(!fullWindows)
    }

    fun enterFullPreview() {
        setFullPreview(true)
    }

    fun exitFullPreview() {
        val needRefreshSeries = previewOrientationChanged
        setFullPreview(false)
        previewOrientationChanged = false
        if (needRefreshSeries) {
            refreshSeriesAfterFullPreview()
        } else {
            syncSeriesSelectionAfterFullPreview()
        }
    }

    private fun refreshSeriesAfterFullPreview() {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        if (vInfo.playFlag.isNullOrEmpty() || sMap[vInfo.playFlag] == null) return
        mGridView?.post {
            mGridView?.recycledViewPool?.clear()
            mGridView?.adapter = seriesAdapter
            refreshList()
        }
    }

    private fun syncSeriesSelectionAfterFullPreview() {
        val vInfo = vodInfo ?: return
        val sMap = vInfo.seriesMap ?: return
        val list = sMap[vInfo.playFlag]
        if (list.isNullOrEmpty()) return
        if (vInfo.playIndex >= list.size) {
            vInfo.playIndex = list.size - 1
        }
        setSeriesGroupOptions()
        mGridView?.post {
            val firstVisible = mGridView?.firstVisiblePosition ?: 0
            val lastVisible = mGridView?.lastVisiblePosition ?: 0
            if (vInfo.playIndex >= 0 && (vInfo.playIndex < firstVisible || vInfo.playIndex > lastVisible)) {
                customSeriesScrollPos(vInfo.playIndex)
            }
        }
        mSeriesGroupView?.post {
            if (mSeriesGroupView?.visibility == View.VISIBLE) {
                mSeriesGroupView?.scrollToPosition(selectedSeriesGroupPosition)
            }
        }
    }

    fun setFullPreview(full: Boolean) {
        if (windowsPreview == null) {
            windowsPreview = llPlayerFragmentContainer?.layoutParams
        }
        if (windowsFull == null) {
            windowsFull = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        if (full) {
            previewOrientation = resources.configuration.orientation
            previewOrientationChanged = false
        }
        fullWindows = full
        setPhoneFullscreen(full)
        requestedOrientation = if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val pf = playFragment
        if (pf != null) {
            pf.setAutoSwitchLineEnabled(!fullWindows)
            pf.setPreviewMode(!fullWindows)
        }
        llPlayerFragmentContainer?.visibility = if (fullWindows || showPreview) View.VISIBLE else View.GONE
        llPlayerFragmentContainer?.layoutParams = if (fullWindows) windowsFull else windowsPreview
        setPreviewRoundClip(!fullWindows)
        llPlayerFragmentContainerBlock?.visibility = if (!fullWindows && showPreview) View.VISIBLE else View.GONE
        mGridView?.visibility = if (fullWindows) View.GONE else View.VISIBLE
        mGridViewFlag?.visibility = if (fullWindows) View.GONE else View.VISIBLE
        if (fullWindows) {
            tvSeriesGroup?.visibility = View.GONE
        } else {
            val list = vodInfo?.seriesMap?.get(vodInfo?.playFlag)
            tvSeriesGroup?.visibility = if (list != null && list.size > 1) View.VISIBLE else View.GONE
            seriesFlagAdapter?.notifyDataSetChanged()
            if (showPreview) {
                mGridView?.requestFocus()
            } else {
                playFragment?.pauseForHidden()
                mGridView?.requestFocus()
            }
        }
        toggleSubtitleTextSize()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (fullWindows && newConfig.orientation != previewOrientation) {
            previewOrientation = newConfig.orientation
            previewOrientationChanged = true
        }
    }

    fun ensurePlayFragment() {
        if (playFragment != null) return
        val fragment = PlayFragment()
        playFragment = fragment
        supportFragmentManager.beginTransaction().add(R.id.previewPlayer, fragment).commitNowAllowingStateLoss()
        fragment.setPreviewMode(!fullWindows)
    }

    fun releasePlayFragment() {
        val fragment = playFragment ?: return
        supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
        playFragment = null
    }

    fun toggleSubtitleTextSize() {
        var subtitleTextSize = SubtitleHelper.getTextSize(this)
        if (!fullWindows) {
            subtitleTextSize = (subtitleTextSize * 0.6).toInt()
        }
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize))
    }

    fun getPlayFragment(): PlayFragment? {
        return playFragment
    }

    private fun setTvPlayUrl(url: String) {
        setTextShow(tvPlayUrl, "播放地址：", url)
    }
}
