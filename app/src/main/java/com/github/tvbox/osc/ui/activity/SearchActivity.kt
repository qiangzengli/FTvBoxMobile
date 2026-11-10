package com.github.tvbox.osc.ui.activity

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.chad.library.adapter.base.BaseQuickAdapter
import com.fbox.mobile.MobileSheets
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.PinyinAdapter
import com.github.tvbox.osc.ui.adapter.SearchAdapter
import com.github.tvbox.osc.ui.dialog.RemoteDialog
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
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
class SearchActivity : BaseActivity() {

    companion object {
        private const val HOT_SEARCH_URL = "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0"
        private const val SEARCH_THREAD_COUNT = 6
        private val SEARCH_MAX_THREAD_COUNT = if (Build.VERSION.SDK_INT >= 35) 24 else if (Build.VERSION.SDK_INT >= 30) 18 else 12
        private const val SEARCH_NEXT_BATCH_SECONDS = 3
        private const val SEARCH_SITE_TIMEOUT_SECONDS = 15
        private val DEFAULT_HOT_WORDS = arrayOf(
            "家业",
            "主角",
            "低智商犯罪",
            "苏超",
            "书卷一梦",
            "美人余",
            "藏海传",
            "长安的荔枝",
            "庆余年",
            "凡人修仙传"
        )

        private var mCheckSources: HashMap<String, String>? = null
        private var hots: ArrayList<String>? = null
        private var hotWordsRequested = false
        var hasKeyBoard: Boolean? = null
        var isSearchBack: Boolean? = null

        @JvmStatic
        fun setCheckedSourcesForSearch(checkedSources: HashMap<String, String>?) {
            mCheckSources = checkedSources
        }
    }

    private var llLayout: LinearLayout? = null
    private var showingResults = false
    private var llHistoryWord: LinearLayout? = null
    private var mGridView: TvRecyclerView? = null
    private var mGridViewWord: TvRecyclerView? = null
    private var historyWordGrid: GridLayout? = null
    var sourceViewModel: SourceViewModel? = null
    private var remoteDialog: RemoteDialog? = null
    private var etSearch: EditText? = null
    private var tvSearch: TextView? = null
    private var tvClear: TextView? = null
    private var tvHistoryClear: ImageView? = null
    private var keyboard: SearchKeyboard? = null
    private var searchAdapter: SearchAdapter? = null
    private var wordAdapter: PinyinAdapter? = null
    private var hotWordAdapter: PinyinAdapter? = null
    private var searchTitle = ""
    private val highMatchVods = ArrayList<Movie.Video>()
    private var showHighMatchResults = false
    private var tvSearchCheckboxBtn: TextView? = null
    private var mSearchCheckboxDialog: SearchCheckboxDialog? = null
    private var wordsSwitch: TextView? = null
    private var aggregateSearchMode = false
    private var aggregateSearchModeInited = false

    override fun getLayoutResID(): Int {
        return R.layout.activity_search
    }

    override fun init() {
        initView()
        initViewModel()
        initData()
        hasKeyBoard = true
        isSearchBack = false
    }

    override fun onResume() {
        super.onResume()
        if (searchPaused) {
            resumePausedSearches()
        }
        requestSearchFocusWhenReady()
        applySearchWordMode()
        refreshSearchHistoryWords()
        val currentHots = hots
        if (currentHots != null && currentHots.isNotEmpty()) {
            hotWordAdapter?.setNewData(currentHots)
        }
    }

    private fun requestSearchFocusWhenReady() {
        if (!showingResults && etSearch != null) etSearch?.requestFocus()
    }

    private fun initView() {
        EventBus.getDefault().register(this)
        llLayout = findViewById(R.id.llLayout)
        llHistoryWord = findViewById(R.id.llHistoryWord)
        etSearch = findViewById(R.id.etSearch)
        tvSearch = findViewById(R.id.tvSearch)
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn)
        tvClear = findViewById(R.id.tvClear)
        mGridView = findViewById(R.id.mGridView)
        keyboard = findViewById(R.id.keyBoardRoot)
        mGridViewWord = findViewById(R.id.mGridViewWord)
        historyWordGrid = findViewById(R.id.historyWordGrid)
        tvHistoryClear = findViewById(R.id.tvHistoryClear)
        mGridViewWord?.setHasFixedSize(true)
        wordAdapter = PinyinAdapter()
        hotWordAdapter = PinyinAdapter()
        wordsSwitch = findViewById(R.id.wordSwitch)
        applySearchWordMode()
        wordAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, _, position ->
            val item = wordAdapter?.getItem(position)
            if (item != null) startSearch(item)
        }
        hotWordAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, _, position ->
            val item = hotWordAdapter?.getItem(position)
            if (item != null) startSearch(item)
        }
        mGridView?.setHasFixedSize(true)
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) {
            mGridView?.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)
        } else {
            mGridView?.layoutManager = V7GridLayoutManager(this.mContext, 3)
        }
        searchAdapter = SearchAdapter()
        mGridView?.adapter = searchAdapter
        searchAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapter?.data?.getOrNull(position)
            if (video != null) {
                openSearchVideo(video)
            }
        }
        tvHistoryClear?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            HistoryHelper.clearSearchHistory()
            refreshSearchHistoryWords()
            Toast.makeText(mContext, "已清空搜索历史", Toast.LENGTH_SHORT).show()
        }
        tvSearch?.setOnClickListener { startSearch(etSearch?.text?.toString() ?: "") }
        tvClear?.setOnClickListener {
            resetSearchResults()
            etSearch?.setText("")
            etSearch?.requestFocus()
        }
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                tvClear?.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(value: Editable?) {}
        })
        etSearch?.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                startSearch(etSearch?.text?.toString() ?: "")
                true
            } else {
                false
            }
        }
        llLayout?.let { setLoadSir(it) }
        tvSearchCheckboxBtn?.setOnClickListener {
            val searchAbleSource = ApiConfig.get().getSearchSourceBeanList()
            if (mSearchCheckboxDialog == null) {
                mSearchCheckboxDialog = SearchCheckboxDialog(this@SearchActivity, searchAbleSource.toMutableList(), mCheckSources)
            } else {
                if (searchAbleSource.size != mSearchCheckboxDialog?.mSourceList?.size) {
                    mSearchCheckboxDialog?.mSourceList = searchAbleSource.toMutableList()
                }
            }
            mSearchCheckboxDialog?.setOnDismissListener { dialog ->
                dialog.dismiss()
            }
            mSearchCheckboxDialog?.show()
        }
    }

    private fun startSearch(wd: String?) {
        val keyword = wd?.trim() ?: ""
        if (keyword.isEmpty()) {
            etSearch?.error = "请输入搜索内容"
            return
        }
        hiddenImm()
        etSearch?.setText(keyword)
        etSearch?.setSelection(keyword.length)
        HistoryHelper.setSearchHistory(keyword)
        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            val bundle = Bundle()
            bundle.putString("title", keyword)
            jumpActivity(FastSearchActivity::class.java, bundle)
        } else {
            search(keyword)
        }
    }

    private fun showSearchResults(show: Boolean) {
        showingResults = show
        findViewById<View>(R.id.mobileSearchSuggestions)?.visibility = if (show) View.GONE else View.VISIBLE
        findViewById<View>(R.id.mobileSearchResults)?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun resetSearchResults() {
        pauseSearchTasks()
        cancel()
        currentSearchToken = ""
        searchPaused = false
        searchTitle = ""
        folderHistory.clear()
        folderLoading = false
        searchAdapter?.setNewData(ArrayList())
        showSearchResults(false)
        refreshSearchHistoryWords()
    }

    private fun setAggregateHotTitle() {
        wordsSwitch?.text = "热门搜索"
    }

    private fun setNormalWordTitle() {
        wordsSwitch?.text = "热门搜索"
    }

    private fun applySearchWordMode() {
        aggregateSearchMode = Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)
        if (!aggregateSearchModeInited) {
            aggregateSearchModeInited = true
            wordsSwitch?.text = "热门搜索"
            wordsSwitch?.isClickable = false
            mGridViewWord?.layoutManager = V7GridLayoutManager(this, 2)
            mGridViewWord?.isNestedScrollingEnabled = false
            mGridViewWord?.setHasFixedSize(false)
            mGridViewWord?.adapter = hotWordAdapter
        }
        showSearchResults(showingResults && !aggregateSearchMode)
        refreshSearchHistoryWords()
    }

    private fun setHotWordsData(data: ArrayList<String>) {
        hotWordAdapter?.setNewData(data)
    }

    private fun refreshSearchHistoryWords() {
        val grid = historyWordGrid ?: return
        grid.post {
            if (isFinishing) return@post
            val history = Hawk.get(HawkConfig.SEARCH_HISTORY, ArrayList<String>())
            grid.removeAllViews()
            findViewById<View>(R.id.mobileSearchHistoryEmpty)?.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
            tvHistoryClear?.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
            val itemHeight = MobileSheets.dp(this, 48f)
            val gap = MobileSheets.dp(this, 8f)
            val padding = MobileSheets.dp(this, 12f)
            val availableWidth = grid.width
            if (availableWidth <= 0) return@post
            var row: LinearLayout? = null
            var rowWidth = 0
            for (word in history) {
                val item = TextView(this).apply {
                    text = word
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setTextColor(-0x19150e)
                    textSize = 14f
                    setPadding(padding, 0, padding, 0)
                    setBackgroundResource(R.drawable.mobile_surface)
                }
                item.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY)
                )
                val width = Math.min(availableWidth, Math.max(itemHeight, item.measuredWidth))
                if (row == null || rowWidth + gap + width > availableWidth) {
                    row = LinearLayout(this)
                    val rowParams = GridLayout.LayoutParams().apply {
                        this.width = -1
                        this.height = -2
                        bottomMargin = gap
                    }
                    grid.addView(row, rowParams)
                    rowWidth = 0
                }
                val params = LinearLayout.LayoutParams(width, itemHeight)
                if (rowWidth > 0) params.leftMargin = gap
                row.addView(item, params)
                rowWidth += width + params.leftMargin
                item.setOnClickListener { startSearch(word) }
            }
        }
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
            mGridView?.visibility = View.VISIBLE
            searchAdapter?.setNewData(videoList)
        })
    }

    private fun openSearchVideo(video: Movie.Video) {
        pauseSearchTasks()
        hasKeyBoard = false
        if (TextUtils.equals("folder", video.tag)) {
            val currentData = searchAdapter?.data ?: ArrayList()
            folderHistory.add(ArrayList(currentData))
            folderLoading = true
            showLoading()
            sourceViewModel?.getList(video.sourceKey ?: "", video.id ?: "")
            return
        }
        isSearchBack = true
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
            folderLoading = false
            val previous = folderHistory.removeAt(folderHistory.size - 1)
            showSuccess()
            mGridView?.visibility = View.VISIBLE
            searchAdapter?.setNewData(previous)
            return
        }
        if (showingResults) {
            resetSearchResults()
            return
        }
        super.onBackPressed()
    }

    /**
     * 拼音联想
     */
    private fun loadRec(key: String) {
        OkGo.get<String>("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
            .params("format", "json")
            .params("page_num", 0)
            .params("page_size", 20)
            .params("key", key)
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>) {
                    try {
                        val hotsList = ArrayList<String>()
                        val result = response.body() ?: return
                        val gson = Gson()
                        val json = gson.fromJson(result, JsonElement::class.java)
                        val groupDataArr = json.asJsonObject
                            .get("data").asJsonObject
                            .get("search_data").asJsonObject
                            .get("vecGroupData").asJsonArray
                            .get(0).asJsonObject
                            .get("group_data").asJsonArray
                        for (groupDataElement in groupDataArr) {
                            val groupData = groupDataElement.asJsonObject
                            val keywordTxt = groupData.getAsJsonObject("dtReportInfo")
                                .getAsJsonObject("reportData")
                                .get("keyword_txt").asString
                            hotsList.add(keywordTxt.trim())
                        }
                        wordsSwitch?.text = "猜你 想搜"
                        setHotWordsData(hotsList)
                        mGridViewWord?.smoothScrollToPosition(0)
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }

                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()?.string() ?: ""
                }
            })
    }

    private fun useDefaultHotWords() {
        val data = ArrayList<String>()
        for (word in DEFAULT_HOT_WORDS) {
            data.add(word)
        }
        cacheHotWords(data)
    }

    private fun cacheHotWords(data: ArrayList<String>) {
        hots = data
        setHotWordsData(data)
    }

    private fun cleanHotWord(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        return title.trim().replace(Regex("[<>《》\\-]"), "").split(" ")[0]
    }

    private fun addHotWord(data: ArrayList<String>, title: String?) {
        val word = cleanHotWord(title)
        if (!TextUtils.isEmpty(word) && !data.contains(word)) {
            data.add(word)
        }
    }

    private fun initData() {
        initCheckedSourcesForSearch()
        applySearchWordMode()
        val intent = intent
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            intent.removeExtra("title")
            startSearch(title)
        }
        if (aggregateSearchMode) {
            setAggregateHotTitle()
            refreshSearchHistoryWords()
        } else {
            setNormalWordTitle()
        }
        val currentHots = hots
        if (currentHots != null && currentHots.isNotEmpty()) {
            setHotWordsData(currentHots)
            return
        }
        if (hotWordsRequested) {
            return
        }
        hotWordsRequested = true
        useDefaultHotWords()
        // 加载热词
        OkGo.get<String>(HOT_SEARCH_URL)
            .headers("User-Agent", "Mozilla/5.0")
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>) {
                    try {
                        val data = ArrayList<String>()
                        val itemList = JsonParser.parseString(response.body() ?: "").asJsonObject.get("subjects").asJsonArray
                        for (ele in itemList) {
                            val obj = ele as JsonObject
                            if (obj.has("title")) {
                                addHotWord(data, obj.get("title").asString)
                            }
                        }
                        if (data.isEmpty()) {
                            useDefaultHotWords()
                            return
                        }
                        cacheHotWords(data)
                    } catch (th: Throwable) {
                        th.printStackTrace()
                        useDefaultHotWords()
                    }
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    useDefaultHotWords()
                }

                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()?.string() ?: ""
                }
            })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            startSearch(event.obj as? String)
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
        }
    }

    private fun initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch()
    }

    private fun search(title: String) {
        cancel()
        if (remoteDialog != null) {
            remoteDialog?.dismiss()
            remoteDialog = null
        }
        showSearchResults(true)
        showLoading()
        etSearch?.setText(title)

        //写入历史记录
        HistoryHelper.setSearchHistory(title)

        this.searchTitle = title
        mGridView?.visibility = View.INVISIBLE
        searchAdapter?.setNewData(ArrayList())
        searchResult()
    }

    private var searchExecutorService: ExecutorService? = null
    private var searchTimeoutExecutor: ScheduledExecutorService? = null
    private val allRunCount = AtomicInteger(0)
    private val pendingSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val waitingSearchTasks: MutableList<SearchTask> = Collections.synchronizedList(ArrayList<SearchTask>())
    private val startedSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val releasedSearchKeys: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val searchTokenSeq = AtomicInteger(0)
    private val totalSearchCount = AtomicInteger(0)
    private var currentSearchToken = ""
    private var searchPaused = false
    private val detailFallbackSearchResults = ArrayList<Movie.Video>()
    private val folderHistory = ArrayList<List<Movie.Video>>()
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
            allRunCount.set(0)
            pendingSearchKeys.clear()
            waitingSearchTasks.clear()
            startedSearchKeys.clear()
            releasedSearchKeys.clear()
            highMatchVods.clear()
            detailFallbackSearchResults.clear()
            folderHistory.clear()
            showHighMatchResults = false
            totalSearchCount.set(0)
            currentSearchToken = searchTokenSeq.incrementAndGet().toString()
            searchPaused = false
        }
        val searchRequestList = ArrayList<SourceBean>()
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList())
        val home = ApiConfig.get().getHomeSourceBean()
        if (home != null) {
            searchRequestList.remove(home)
            searchRequestList.add(0, home)
        }

        val searchTasks = ArrayList<SearchTask>()
        for (bean in searchRequestList) {
            if (!bean.isSearchable()) {
                continue
            }
            val checkSources = mCheckSources
            if (checkSources != null && !checkSources.containsKey(bean.key)) {
                continue
            }
            searchTasks.add(SearchTask(bean.key, searchTitle, currentSearchToken, isBlockingSearchSource(bean)))
        }
        if (searchTasks.isEmpty()) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show()
            showEmpty()
            return
        }
        for (task in searchTasks) {
            pendingSearchKeys.add(task.sourceKey)
        }
        allRunCount.set(searchTasks.size)
        totalSearchCount.set(searchTasks.size)
        searchExecutorService = createSearchExecutor()
        searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        startFastSearchTasks(searchTasks)
        waitingSearchTasks.addAll(searchTasks)
        startNextSearchBatch(currentSearchToken)
    }

    private fun matchSearchResult(name: String?, searchTitle: String?): Boolean {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false
        return TextUtils.equals(name?.trim(), searchTitle?.trim())
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

    private fun addSearchResults(data: List<Movie.Video>?) {
        if (data.isNullOrEmpty()) return
        if ((searchAdapter?.data?.size ?: 0) > 0) {
            searchAdapter?.addData(data)
        } else {
            showSuccess()
            mGridView?.visibility = View.VISIBLE
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
        val videoList = absXml?.movie?.videoList
        if (videoList != null && videoList.isNotEmpty()) {
            val exactData = ArrayList<Movie.Video>()
            val highData = ArrayList<Movie.Video>()
            for (video in videoList) {
                if (isHighMatchSearchResult(video)) {
                    highMatchVods.add(video)
                    highData.add(video)
                    detailFallbackSearchResults.add(video)
                }
                if (matchSearchResult(video.name, searchTitle)) {
                    exactData.add(video)
                }
            }

            if (showHighMatchResults) {
                addSearchResults(highData)
            } else if (exactData.isNotEmpty()) {
                addSearchResults(exactData)
            }
        }

        if (shouldShowHighMatchResults()) {
            showHighMatchResults = true
            addSearchResults(ArrayList(highMatchVods))
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

    private fun scheduleSearchTimeout(sourceKey: String, searchToken: String) {
        val executor = searchTimeoutExecutor ?: return
        executor.schedule({
            if (!isCurrentSearchToken(searchToken)) return@schedule
            if (markSearchFinished(sourceKey, searchToken)) {
                releaseSearchSlotAndStartNext(sourceKey, searchToken)
                runOnUiThread {
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

    private fun startFastSearchTasks(tasks: List<SearchTask>) {
        for (task in tasks) {
            if (task.blocking) {
                continue
            }
            if (startedSearchKeys.add(task.sourceKey)) {
                submitDirectSearchTask(task)
            }
        }
    }

    private fun submitDirectSearchTask(task: SearchTask) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return
        scheduleSearchTimeout(task.sourceKey, task.searchToken)
        try {
            sourceViewModel?.getSearch(task.sourceKey, task.title, task.searchToken)
        } catch (th: Throwable) {
            th.printStackTrace()
            if (markSearchFinished(task.sourceKey, task.searchToken)) {
                finishSearchIfDone()
            }
        }
    }

    private fun getPendingSearchKeys(): List<String> {
        synchronized(pendingSearchKeys) {
            return ArrayList(pendingSearchKeys)
        }
    }

    private fun finishSearchIfDone() {
        if (allRunCount.get() > 0) return
        searchPaused = false
        if ((searchAdapter?.data?.size ?: 0) <= 0) {
            showEmpty()
        }
        cancel()
        if (searchTimeoutExecutor != null) {
            searchTimeoutExecutor?.shutdownNow()
            searchTimeoutExecutor = null
        }
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

    private fun hiddenImm() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val windowToken = etSearch?.windowToken
        if (imm != null && windowToken != null) {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }
}
