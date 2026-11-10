package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.FastListAdapter
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.lzy.okgo.OkGo
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
class FastSearchActivity : BaseActivity() {

    companion object {
        private const val SEARCH_THREAD_COUNT = 6
        private val SEARCH_MAX_THREAD_COUNT = if (Build.VERSION.SDK_INT >= 35) 24 else if (Build.VERSION.SDK_INT >= 30) 18 else 12
        private const val SEARCH_PUMP_SECONDS = 2
        private const val SEARCH_NEXT_BATCH_SECONDS = 3
        private const val SEARCH_SITE_TIMEOUT_SECONDS = 15
        private const val POSTER_FOCUS_ANIM_DURATION = 300L
        private const val POSTER_FOCUS_SCALE = 1.05f
        private const val SEARCH_ALL_NAME = "全部"
    }

    private var llLayout: LinearLayout? = null
    private var mSearchTitle: TextView? = null
    private var mGridView: TvRecyclerView? = null
    private var mGridViewFilter: TvRecyclerView? = null
    private var mGridViewWord: TvRecyclerView? = null
    private var mGridViewWordFenci: TvRecyclerView? = null
    var sourceViewModel: SourceViewModel? = null

    private var searchWordAdapter: SearchWordAdapter? = null
    private var searchAdapter: FastSearchAdapter? = null
    private var searchAdapterFilter: FastSearchAdapter? = null
    private var spListAdapter: FastListAdapter? = null
    private var searchTitle = ""
    private var spNames: HashMap<String, String>? = null
    private var isFilterMode = false
    private var searchFilterKey = ""
    private var resultVods: HashMap<String, ArrayList<Movie.Video>>? = null
    private val highMatchVods = ArrayList<Movie.Video>()
    private var showHighMatchResults = false
    private val quickSearchWord = ArrayList<String>()
    private val wordListNames = HashSet<String>()
    private var wordListVersion = 0
    private var selectedWordName = ""
    private var mCheckSources: HashMap<String, String>? = null

    override fun getLayoutResID(): Int {
        return R.layout.activity_fast_search
    }

    override fun init() {
        spNames = HashMap()
        resultVods = HashMap()
        initView()
        initViewModel()
        initData()
    }

    override fun onResume() {
        super.onResume()
        if (searchPaused) {
            resumePausedSearches()
        }
    }

    private fun initView() {
        EventBus.getDefault().register(this)
        llLayout = findViewById(R.id.llLayout)
        mSearchTitle = findViewById(R.id.mSearchTitle)
        mGridView = findViewById(R.id.mGridView)
        mGridViewWord = findViewById(R.id.mGridViewWord)
        mGridViewFilter = findViewById(R.id.mGridViewFilter)

        mGridViewWord?.setHasFixedSize(true)
        mGridViewWord?.layoutManager = V7LinearLayoutManager(this.mContext, if (isPhonePortrait()) 0 else 1, false)
        spListAdapter = FastListAdapter()
        mGridViewWord?.adapter = spListAdapter

        spListAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, _, position ->
            val item = spListAdapter?.getItem(position)
            if (item != null) selectWord(item)
        }

        mGridViewWord?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {}

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                val item = spListAdapter?.getItem(position)
                if (item != null) selectWord(item)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                val item = spListAdapter?.getItem(position)
                if (item != null) selectWord(item)
            }
        })
        mGridViewWord?.setOnInBorderKeyEventListener { direction, _ ->
            direction == View.FOCUS_UP
        }

        mGridView?.setHasFixedSize(true)
        mGridView?.layoutManager = V7GridLayoutManager(this.mContext, if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) 1 else if (isPhonePortrait()) 3 else 4)

        searchAdapter = FastSearchAdapter()
        mGridView?.adapter = searchAdapter
        mGridView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                setPosterFocusScale(itemView, false)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                setPosterFocusScale(itemView, true)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {}
        })

        searchAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapter?.data?.getOrNull(position)
            if (video != null) {
                openSearchVideo(video, false)
            }
        }

        mGridViewFilter?.layoutManager = V7GridLayoutManager(this.mContext, if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) 1 else if (isPhonePortrait()) 3 else 4)
        searchAdapterFilter = FastSearchAdapter()
        mGridViewFilter?.adapter = searchAdapterFilter
        mGridViewFilter?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                setPosterFocusScale(itemView, false)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                setPosterFocusScale(itemView, true)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {}
        })
        searchAdapterFilter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapterFilter?.data?.getOrNull(position)
            if (video != null) {
                openSearchVideo(video, true)
            }
        }

        llLayout?.let { setLoadSir(it) }

        // 分词
        searchWordAdapter = SearchWordAdapter()
        mGridViewWordFenci = findViewById(R.id.mGridViewWordFenci)
        mGridViewWordFenci?.adapter = searchWordAdapter
        mGridViewWordFenci?.layoutManager = V7LinearLayoutManager(this.mContext, 0, false)
        searchWordAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, _, position ->
            val str = searchWordAdapter?.data?.getOrNull(position)
            if (str != null) search(str)
        }
        searchWordAdapter?.setNewData(ArrayList())
    }

    private fun setPosterFocusScale(itemView: View?, focused: Boolean) {
        if (itemView == null) return
        if (focused) {
            itemView.bringToFront()
        }
        val scale = if (focused) POSTER_FOCUS_SCALE else 1.0f
        itemView.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(POSTER_FOCUS_ANIM_DURATION)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel?.listResult?.observe(this, Observer { data ->
            if (!folderLoading) return@Observer
            folderLoading = false
            val movie = data?.movie
            val videoList = movie?.videoList
            if (data == null || movie == null || videoList == null) {
                showEmpty()
                return@Observer
            }
            showSuccess()
            if (folderFilterMode) {
                mGridView?.visibility = View.GONE
                mGridViewFilter?.visibility = View.VISIBLE
                searchAdapterFilter?.setNewData(videoList)
            } else {
                mGridViewFilter?.visibility = View.GONE
                mGridView?.visibility = View.VISIBLE
                searchAdapter?.setNewData(videoList)
            }
        })
    }

    private fun openSearchVideo(video: Movie.Video, filterMode: Boolean) {
        pauseSearchTasks()
        if (TextUtils.equals("folder", video.tag)) {
            val list = if (filterMode) searchAdapterFilter?.data else searchAdapter?.data
            folderHistory.add(ArrayList(list ?: ArrayList()))
            folderHistoryFilter.add(filterMode)
            folderHistorySiteKeys.add(getSelectedSearchSiteKey())
            folderFilterMode = filterMode
            folderLoading = true
            showLoading()
            sourceViewModel?.getList(video.sourceKey ?: "", video.id ?: "")
            return
        }
        val bundle = Bundle().apply {
            putString("id", video.id)
            putString("sourceKey", video.sourceKey)
            putString("title", video.name)
            putString("picture", video.pic)
        }
        putDetailFallbackCandidates(bundle, video)
        jumpActivity(DetailActivity::class.java, bundle)
    }

    override fun onBackPressed() {
        if (folderHistory.isNotEmpty()) {
            val currentSiteKey = getSelectedSearchSiteKey()
            val folderSiteKey = folderHistorySiteKeys[folderHistorySiteKeys.size - 1]
            if (!TextUtils.equals(currentSiteKey, folderSiteKey)) {
                folderLoading = false
                folderHistory.clear()
                folderHistoryFilter.clear()
                folderHistorySiteKeys.clear()
                super.onBackPressed()
                return
            }
            folderLoading = false
            val previous = folderHistory.removeAt(folderHistory.size - 1)
            val filterMode = folderHistoryFilter.removeAt(folderHistoryFilter.size - 1)
            folderHistorySiteKeys.removeAt(folderHistorySiteKeys.size - 1)
            folderFilterMode = filterMode
            showSuccess()
            if (filterMode) {
                mGridView?.visibility = View.GONE
                mGridViewFilter?.visibility = View.VISIBLE
                searchAdapterFilter?.setNewData(previous)
            } else {
                mGridViewFilter?.visibility = View.GONE
                mGridView?.visibility = View.VISIBLE
                searchAdapter?.setNewData(previous)
            }
            return
        }
        super.onBackPressed()
    }

    private fun getSelectedSearchSiteKey(): String {
        if (TextUtils.isEmpty(selectedWordName) || TextUtils.equals(selectedWordName, SEARCH_ALL_NAME)) {
            return SEARCH_ALL_NAME
        }
        val key = spNames?.get(selectedWordName)
        return if (TextUtils.isEmpty(key)) selectedWordName else (key ?: "")
    }

    private fun filterResult(spName: String?) {
        if (spName.isNullOrEmpty()) return
        selectedWordName = spName
        setSelectedWordName(spName)
        if (TextUtils.equals(spName, SEARCH_ALL_NAME)) {
            mGridView?.visibility = View.VISIBLE
            mGridViewFilter?.visibility = View.GONE
            return
        }
        mGridView?.visibility = View.GONE
        mGridViewFilter?.visibility = View.VISIBLE
        val key = spNames?.get(spName)
        if (key.isNullOrEmpty()) return

        if (TextUtils.equals(searchFilterKey, key)) return
        searchFilterKey = key

        val list = resultVods?.get(key) ?: ArrayList()
        searchAdapterFilter?.setNewData(list)
    }

    private fun selectWord(spName: String?) {
        if (spName.isNullOrEmpty() || TextUtils.equals(selectedWordName, spName)) return
        filterResult(spName)
    }

    private fun updateWordListWhenIdle(action: Runnable?) {
        if (action == null) return
        val grid = mGridViewWord
        if (grid == null) {
            action.run()
            return
        }
        if (grid.isComputingLayout) {
            grid.post {
                updateWordListWhenIdle(action)
            }
            return
        }
        action.run()
    }

    private fun setSelectedWordName(spName: String) {
        updateWordListWhenIdle(Runnable {
            spListAdapter?.setSelectedName(spName)
            val grid = mGridViewWord
            if (grid != null) {
                spListAdapter?.refreshVisibleSelection(grid)
            }
        })
    }

    private fun setWordListData(data: List<String>) {
        val wordList = ArrayList(data)
        val version = ++wordListVersion
        wordListNames.clear()
        wordListNames.addAll(wordList)
        updateWordListWhenIdle(Runnable {
            if (version != wordListVersion) return@Runnable
            spListAdapter?.setNewData(wordList)
            if (wordList.isNotEmpty() && TextUtils.equals(wordList[0], SEARCH_ALL_NAME)) {
                mGridViewWord?.setSelectedPosition(0)
                mGridViewWord?.setSelection(0)
                requestWordListFirstFocus()
            }
        })
    }

    private fun requestWordListFirstFocus() {
        val grid = mGridViewWord ?: return
        grid.post {
            if (isFinishing || mGridViewWord == null) return@post
            mGridViewWord?.setSelectedPosition(0)
            mGridViewWord?.setSelection(0)
            val firstChild = mGridViewWord?.getChildAt(0)
            if (firstChild != null) {
                firstChild.requestFocus()
            } else {
                mGridViewWord?.requestFocus()
            }
        }
    }

    private fun addWordListDataIfAbsent(name: String?) {
        if (name.isNullOrEmpty() || !wordListNames.add(name)) return
        val version = wordListVersion
        updateWordListWhenIdle(Runnable {
            if (version != wordListVersion) return@Runnable
            val names = spListAdapter?.data ?: ArrayList()
            for (i in 0 until names.size) {
                if (TextUtils.equals(name, names[i])) {
                    return@Runnable
                }
            }
            spListAdapter?.addData(name)
        })
    }

    private fun fenci() {
        if (quickSearchWord.isNotEmpty()) return
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle))
        val words = ArrayList(LinkedHashSet(quickSearchWord))
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words))
    }

    private fun initData() {
        initCheckedSourcesForSearch()
        val intent = intent
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title") ?: ""
            showLoading()
            search(title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            val title = event.obj as? String ?: ""
            showLoading()
            search(title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj as? AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD) {
            val data = event.obj as? List<String>
            if (data != null) {
                searchWordAdapter?.setNewData(data)
            }
        }
        updateSearchStatus()
    }

    private fun initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch()
    }

    private fun search(title: String) {
        cancel()
        showLoading()
        this.searchTitle = title
        fenci()
        mGridView?.visibility = View.INVISIBLE
        mGridViewFilter?.visibility = View.GONE
        searchAdapter?.setNewData(ArrayList())
        searchAdapterFilter?.setNewData(ArrayList())

        selectedWordName = ""
        filterResult(SEARCH_ALL_NAME)
        resultVods?.clear()
        highMatchVods.clear()
        showHighMatchResults = false
        searchFilterKey = ""
        isFilterMode = false
        spNames?.clear()
        totalSearchCount.set(0)
        timedOutSearchCount.set(0)
        updateSearchStatus()

        //写入历史记录
        HistoryHelper.setSearchHistory(title)

        searchResult()
    }

    private var searchExecutorService: ExecutorService? = null
    private var searchTimeoutExecutor: ScheduledExecutorService? = null
    private val allRunCount = AtomicInteger(0)
    private val pendingSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val waitingSearchTasks: MutableList<SearchTask> = Collections.synchronizedList(ArrayList<SearchTask>())
    private val startedSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val releasedSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private var startedSearchCountOffset = 0
    private val searchTokenSeq = AtomicInteger(0)
    private val totalSearchCount = AtomicInteger(0)
    private val timedOutSearchCount = AtomicInteger(0)
    private var currentSearchToken = ""
    private var searchPaused = false
    private val detailFallbackSearchResults = ArrayList<Movie.Video>()
    private var folderFilterMode = false
    private val folderHistory = ArrayList<List<Movie.Video>>()
    private val folderHistoryFilter = ArrayList<Boolean>()
    private val folderHistorySiteKeys = ArrayList<String>()
    private var folderLoading = false

    private fun searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService?.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor?.shutdownNow()
                searchTimeoutExecutor = null
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        } finally {
            searchAdapter?.setNewData(ArrayList())
            searchAdapterFilter?.setNewData(ArrayList())
            allRunCount.set(0)
            pendingSearchKeys.clear()
            waitingSearchTasks.clear()
            startedSearchKeys.clear()
            releasedSearchKeys.clear()
            startedSearchCountOffset = 0
            currentSearchToken = searchTokenSeq.incrementAndGet().toString()
            searchPaused = false
            totalSearchCount.set(0)
            timedOutSearchCount.set(0)
            detailFallbackSearchResults.clear()
            folderHistory.clear()
            folderHistoryFilter.clear()
            folderHistorySiteKeys.clear()
            updateSearchStatus()
        }
        val searchRequestList = ArrayList<SourceBean>()
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList())
        val home = ApiConfig.get().getHomeSourceBean()
        if (home != null) {
            searchRequestList.remove(home)
            searchRequestList.add(0, home)
        }

        val fastSearchTasks = ArrayList<SearchTask>()
        val blockingSearchTasks = ArrayList<SearchTask>()
        val hots = ArrayList<String>()
        hots.add(SEARCH_ALL_NAME)

        setWordListData(hots)
        for (bean in searchRequestList) {
            if (!bean.isSearchable()) {
                continue
            }
            val checkSources = mCheckSources
            if (checkSources != null && !checkSources.containsKey(bean.key)) {
                continue
            }
            val task = SearchTask(bean.key, searchTitle, currentSearchToken, isBlockingSearchSource(bean))
            if (task.blocking) {
                blockingSearchTasks.add(task)
            } else {
                fastSearchTasks.add(task)
            }
            spNames?.put(bean.name, bean.key)
        }
        val searchTasks = ArrayList<SearchTask>()
        searchTasks.addAll(fastSearchTasks)
        searchTasks.addAll(blockingSearchTasks)

        if (searchTasks.isEmpty()) {
            showEmpty()
            setSearchStatusText("无搜索源", "结果 0")
            return
        }
        for (task in searchTasks) {
            pendingSearchKeys.add(task.sourceKey)
        }
        allRunCount.set(searchTasks.size)
        totalSearchCount.set(searchTasks.size)
        updateSearchStatus()
        searchExecutorService = createSearchExecutor()
        searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        waitingSearchTasks.addAll(searchTasks)
        startNextSearchBatch(currentSearchToken)
        startSearchPump(currentSearchToken)
        updateSearchStatus()
    }

    private fun addWordAdapterIfNeed(key: String): String {
        return try {
            var name = ""
            val namesMap = spNames
            if (namesMap != null) {
                for (n in namesMap.keys) {
                    if (TextUtils.equals(namesMap[n], key)) {
                        name = n
                    }
                }
            }
            if (TextUtils.isEmpty(name)) return key
            addWordListDataIfAbsent(name)
            key
        } catch (e: Exception) {
            key
        }
    }

    private fun matchSearchResult(name: String?, searchTitle: String?): Boolean {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false
        val title = searchTitle?.trim() ?: ""
        val arr = title.split("\\s+".toRegex())
        var matchNum = 0
        for (one in arr) {
            if (name?.contains(one) == true) matchNum++
        }
        return matchNum == arr.size
    }

    private fun isExactSearchResult(video: Movie.Video?): Boolean {
        return video != null && !TextUtils.isEmpty(video.name) && !TextUtils.isEmpty(searchTitle)
                && TextUtils.equals(video.name?.trim(), searchTitle.trim())
    }

    private fun isHighMatchSearchResult(video: Movie.Video?): Boolean {
        return video != null && !TextUtils.isEmpty(video.name) && !TextUtils.isEmpty(searchTitle)
                && (video.name?.replace("\\s+".toRegex(), "") ?: "").startsWith(searchTitle.replace("\\s+".toRegex(), ""))
    }

    private fun shouldShowHighMatchResults(): Boolean {
        if (showHighMatchResults || (searchAdapter?.data?.size ?: 0) > 0) return false
        val total = totalSearchCount.get()
        val threshold = Math.min(SEARCH_THREAD_COUNT, total)
        return threshold > 0 && total - allRunCount.get() >= threshold
    }

    private fun addMainSearchResults(data: List<Movie.Video>?) {
        if (data.isNullOrEmpty()) return
        if ((searchAdapter?.data?.size ?: 0) > 0) {
            searchAdapter?.addData(data)
        } else {
            showSuccess()
            if (!isFilterMode) mGridView?.visibility = View.VISIBLE
            searchAdapter?.setNewData(data)
        }
    }

    private fun searchData(absXml: AbsXml?) {
        if (!isCurrentSearchResult(absXml)) {
            return
        }
        val sourceKey = absXml?.sourceKey ?: ""
        val token = absXml?.searchToken ?: ""
        if (!markSearchFinished(sourceKey, token)) {
            return
        }
        releaseSearchSlotAndStartNext(sourceKey, token)
        var lastSourceKey = ""
        val exactData = ArrayList<Movie.Video>()
        val highData = ArrayList<Movie.Video>()

        val videoList = absXml?.movie?.videoList
        if (videoList != null && videoList.isNotEmpty()) {
            for (video in videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                detailFallbackSearchResults.add(video)
                val vods = resultVods
                if (vods != null) {
                    if (!vods.containsKey(video.sourceKey)) {
                        vods[video.sourceKey ?: ""] = ArrayList()
                    }
                    vods[video.sourceKey ?: ""]?.add(video)
                }
                if (isHighMatchSearchResult(video)) {
                    highMatchVods.add(video)
                    highData.add(video)
                }
                if (isExactSearchResult(video)) {
                    exactData.add(video)
                }
                if (!TextUtils.equals(video.sourceKey, lastSourceKey)) {
                    lastSourceKey = this.addWordAdapterIfNeed(video.sourceKey ?: "")
                }
            }
        }

        if (showHighMatchResults) {
            addMainSearchResults(highData)
        } else if (exactData.isNotEmpty()) {
            addMainSearchResults(exactData)
        } else if (shouldShowHighMatchResults()) {
            showHighMatchResults = true
            addMainSearchResults(ArrayList(highMatchVods))
        }

        finishSearchIfDone()
    }

    private fun putDetailFallbackCandidates(bundle: Bundle?, selectedVideo: Movie.Video?) {
        if (bundle == null || selectedVideo == null || TextUtils.isEmpty(selectedVideo.name)) {
            return
        }
        val title = selectedVideo.name?.trim() ?: ""
        val candidates = ArrayList<Movie.Video>()
        val keys = HashSet<String>()
        for (video in detailFallbackSearchResults) {
            if (video == null || TextUtils.isEmpty(video.id)
                || !TextUtils.equals(title, video.name?.trim() ?: "")
            ) {
                continue
            }
            val key = (video.sourceKey ?: "") + "|" + video.id
            if (keys.add(key)) {
                candidates.add(video)
                if (candidates.size >= 20) {
                    break
                }
            }
        }
        if (candidates.isNotEmpty()) {
            bundle.putSerializable(DetailActivity.EXTRA_DETAIL_FALLBACK_CANDIDATES, candidates)
        }
    }

    private fun scheduleSearchAdvance(sourceKey: String, searchToken: String) {
        val executor = searchTimeoutExecutor ?: return
        executor.schedule({
            if (!isCurrentSearchToken(searchToken)) return@schedule
            if (isSearchPending(sourceKey, searchToken) && releaseSearchSlot(sourceKey, searchToken)) {
                startNextSearchTask(searchToken)
            }
        }, SEARCH_NEXT_BATCH_SECONDS.toLong(), TimeUnit.SECONDS)
    }

    private fun startSearchPump(searchToken: String) {
        val executor = searchTimeoutExecutor ?: return
        executor.scheduleWithFixedDelay({
            try {
                if (!isCurrentSearchToken(searchToken) || allRunCount.get() <= 0) return@scheduleWithFixedDelay
                if (getWaitingSearchCount() > 0) {
                    startNextSearchBatch(searchToken)
                    updateSearchStatusOnUiThread()
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }, SEARCH_PUMP_SECONDS.toLong(), SEARCH_PUMP_SECONDS.toLong(), TimeUnit.SECONDS)
    }

    private fun updateSearchStatusOnUiThread() {
        runOnUiThread {
            updateSearchStatus()
        }
    }

    private fun scheduleSearchTimeout(sourceKey: String, searchToken: String) {
        val executor = searchTimeoutExecutor ?: return
        executor.schedule({
            if (!isCurrentSearchToken(searchToken)) return@schedule
            if (markSearchFinished(sourceKey, searchToken)) {
                timedOutSearchCount.incrementAndGet()
                releaseSearchSlotAndStartNext(sourceKey, searchToken)
                runOnUiThread {
                    updateSearchStatus()
                    finishSearchIfDone()
                }
            }
        }, SEARCH_SITE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    }

    private fun submitSearchTask(task: SearchTask): Boolean {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return false
        val executor = searchExecutorService
        if (executor == null || executor.isShutdown) return false
        try {
            executor.execute(task)
        } catch (e: RejectedExecutionException) {
            return false
        }
        scheduleSearchAdvance(task.sourceKey, task.searchToken)
        scheduleSearchTimeout(task.sourceKey, task.searchToken)
        updateSearchStatusOnUiThread()
        return true
    }

    private fun createSearchExecutor(): ExecutorService {
        return ThreadPoolExecutor(0, SEARCH_MAX_THREAD_COUNT, 30L, TimeUnit.SECONDS, SynchronousQueue<Runnable>())
    }

    private fun startNextSearchBatch(searchToken: String) {
        for (i in 0 until SEARCH_THREAD_COUNT) {
            if (!startNextSearchTask(searchToken)) {
                return
            }
        }
    }

    private fun startNextSearchTask(searchToken: String): Boolean {
        if (!isCurrentSearchToken(searchToken)) return false
        val task = takeNextSearchTask(searchToken) ?: return false
        if (!submitSearchTask(task)) {
            startedSearchKeys.remove(task.sourceKey)
            synchronized(waitingSearchTasks) {
                waitingSearchTasks.add(0, task)
            }
            return false
        }
        return true
    }

    private fun takeNextSearchTask(searchToken: String): SearchTask? {
        synchronized(waitingSearchTasks) {
            while (waitingSearchTasks.isNotEmpty()) {
                val task = waitingSearchTasks.removeAt(0)
                if (!isSearchPending(task.sourceKey, searchToken) || !startedSearchKeys.add(task.sourceKey)) {
                    continue
                }
                return task
            }
        }
        return null
    }

    private fun resumePausedSearches() {
        if (!searchPaused) {
            return
        }
        searchPaused = false
        val sourceKeys = getPendingSearchKeys()
        if (sourceKeys.isEmpty()) {
            finishSearchIfDone()
            return
        }
        currentSearchToken = searchTokenSeq.incrementAndGet().toString()
        waitingSearchTasks.clear()
        startedSearchCountOffset = Math.max(0, totalSearchCount.get() - allRunCount.get())
        startedSearchKeys.clear()
        releasedSearchKeys.clear()
        for (sourceKey in sourceKeys) {
            val bean = ApiConfig.get().getSource(sourceKey)
            waitingSearchTasks.add(SearchTask(sourceKey, searchTitle, currentSearchToken, isBlockingSearchSource(bean)))
        }
        if (searchExecutorService == null || searchExecutorService?.isShutdown == true) {
            searchExecutorService = createSearchExecutor()
        }
        if (searchTimeoutExecutor == null || searchTimeoutExecutor?.isShutdown == true) {
            searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        startNextSearchBatch(currentSearchToken)
        startSearchPump(currentSearchToken)
        updateSearchStatus()
    }

    private fun pauseSearchTasks() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService?.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor?.shutdownNow()
                searchTimeoutExecutor = null
            }
            searchPaused = allRunCount.get() > 0
            if (searchPaused) {
                cancel()
                currentSearchToken = ""
            }
            updateSearchStatus()
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun isCurrentSearchResult(absXml: AbsXml?): Boolean {
        return absXml != null && isCurrentSearchToken(absXml.searchToken)
    }

    private fun isCurrentSearchToken(searchToken: String?): Boolean {
        return !TextUtils.isEmpty(searchToken) && searchToken == currentSearchToken
    }

    private fun markSearchFinished(sourceKey: String, searchToken: String): Boolean {
        if (!isCurrentSearchToken(searchToken)) return false
        synchronized(pendingSearchKeys) {
            if (TextUtils.isEmpty(sourceKey)) {
                return false
            }
            if (!pendingSearchKeys.remove(sourceKey)) {
                return false
            }
            allRunCount.set(pendingSearchKeys.size)
            return true
        }
    }

    private fun releaseSearchSlot(sourceKey: String, searchToken: String): Boolean {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false
        return releasedSearchKeys.add(sourceKey)
    }

    private fun releaseSearchSlotAndStartNext(sourceKey: String, searchToken: String) {
        if (releaseSearchSlot(sourceKey, searchToken)) {
            startNextSearchTask(searchToken)
        }
    }

    private fun isSearchPending(sourceKey: String, searchToken: String): Boolean {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false
        synchronized(pendingSearchKeys) {
            return pendingSearchKeys.contains(sourceKey)
        }
    }

    private fun isBlockingSearchSource(bean: SourceBean?): Boolean {
        return bean == null || bean.getType() == 3
    }

    private fun getPendingSearchKeys(): List<String> {
        synchronized(pendingSearchKeys) {
            return ArrayList(pendingSearchKeys)
        }
    }

    private fun getWaitingSearchCount(): Int {
        synchronized(waitingSearchTasks) {
            return waitingSearchTasks.size
        }
    }

    private fun finishSearchIfDone() {
        if (allRunCount.get() > 0) return
        searchPaused = false
        updateSearchStatus()
        if ((searchAdapter?.data?.size ?: 0) == 0 && (resultVods?.isEmpty() != false)) {
            showEmpty()
        }
        cancel()
        if (searchTimeoutExecutor != null) {
            searchTimeoutExecutor?.shutdownNow()
            searchTimeoutExecutor = null
        }
    }

    private fun getStartedSearchCount(): Int {
        synchronized(startedSearchKeys) {
            return startedSearchCountOffset + startedSearchKeys.size
        }
    }

    private fun getResultCount(): Int {
        return searchAdapter?.data?.size ?: 0
    }

    private fun updateSearchStatus() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            updateSearchStatusOnUiThread()
            return
        }
        val total = totalSearchCount.get()
        val pending = allRunCount.get()
        val finished = Math.max(0, total - pending)
        val started = Math.min(total, getStartedSearchCount())
        val results = getResultCount()
        val timeouts = timedOutSearchCount.get()

        val firstLine: String
        var secondLine: String
        if (total <= 0) {
            firstLine = "准备搜索"
            secondLine = "结果 0"
        } else if (searchPaused && pending > 0) {
            firstLine = "已暂停 $finished/$total"
            secondLine = "结果 $results · 待 $pending"
        } else if (pending <= 0) {
            firstLine = "搜索完成 $results"
            secondLine = "源 $total/$total"
            if (timeouts > 0) {
                secondLine += " · 超时 $timeouts"
            }
        } else if (started >= total) {
            firstLine = if (finished > 0) "等慢源 $pending" else "等待返回 $pending"
            secondLine = "结果 $results · 完成 $finished/$total"
        } else {
            firstLine = "搜源 $started/$total"
            secondLine = "结果 $results · 待 $pending"
        }
        setSearchStatusText(firstLine, secondLine)
    }

    private fun setSearchStatusText(firstLine: String, secondLine: String) {
        val titleView = mSearchTitle ?: return
        val text = "$firstLine\n$secondLine"
        val span = SpannableString(text)
        val split = firstLine.length
        span.setSpan(StyleSpan(Typeface.BOLD), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(RelativeSizeSpan(1.05f), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(RelativeSizeSpan(0.78f), split + 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(-0x33000001), split + 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleView.text = span
    }

    private inner class SearchTask(
        val sourceKey: String,
        val title: String,
        val searchToken: String,
        val blocking: Boolean
    ) : Runnable {
        override fun run() {
            if (!isSearchPending(sourceKey, searchToken)) return
            try {
                sourceViewModel?.getSearch(sourceKey, title, searchToken)
            } catch (th: Throwable) {
                th.printStackTrace()
                if (markSearchFinished(sourceKey, searchToken)) {
                    runOnUiThread {
                        updateSearchStatus()
                        finishSearchIfDone()
                    }
                }
            }
        }
    }

    private fun cancel() {
        OkGo.getInstance().cancelTag("search")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        try {
            if (searchExecutorService != null) {
                searchExecutorService?.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor?.shutdownNow()
                searchTimeoutExecutor = null
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        EventBus.getDefault().unregister(this)
    }
}
