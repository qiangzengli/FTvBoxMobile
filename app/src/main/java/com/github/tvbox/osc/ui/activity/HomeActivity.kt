package com.github.tvbox.osc.ui.activity

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.IntEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.BounceInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.fbox.mobile.MobileHomeScroll
import com.fbox.mobile.MobileSheets
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.adapter.HomePageAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SortAdapter
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.TipDialog
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.UserFragment
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager
import com.github.tvbox.osc.ui.tv.widget.ViewObj
import com.github.tvbox.osc.util.AppManager
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import me.jessyan.autosize.utils.AutoSizeUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.Objects

class HomeActivity : BaseActivity() {
    private var topLayout: LinearLayout? = null
    private var contentLayout: LinearLayout? = null
    private var mobileScroll: MobileHomeScroll? = null

    fun bindMobileHomeActions(actions: View?) {
        if (actions != null) {
            mobileScroll?.bindActions(actions)
        }
    }

    private var tvDate: TextView? = null
    private var tvName: TextView? = null
    private var mGridView: TvRecyclerView? = null
    private var mViewPager: NoScrollViewPager? = null
    private var sourceViewModel: SourceViewModel? = null
    private var sortAdapter: SortAdapter? = null
    private var pageAdapter: HomePageAdapter? = null
    private var currentView: View? = null
    private val fragments: MutableList<BaseLazyFragment> = ArrayList()
    private var isDownOrUp = false
    private var sortChange = false
    private var currentSelected = 0
    private var sortFocused = 0
    @JvmField
    var sortFocusView: View? = null
    private var loadingSourceKey: String? = null
    private var previousHomeName: String? = null
    private var previousHomeSource: SourceBean? = null
    private var homeSortLoading = false
    private var refreshHomeRec = false
    private val mHandler = Handler(Looper.getMainLooper())
    private var mExitTime: Long = 0
    private var eventBusRegistered = false

    private val mRunnable: Runnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            val date = Date()
            val timeFormat = SimpleDateFormat("yyyy/MM/dd  E  HH:mm", Locale.CHINA)
            tvDate?.text = timeFormat.format(date)
            mHandler.postDelayed(this, 1000)
        }
    }

    private val refreshTopInfoTextSizeRunnable = Runnable {
        refreshTopInfoTextSize()
    }

    private val refreshTopLayoutRunnable = Runnable {
        val top = topLayout
        if (top == null || isActivityUnavailable() || currentSelected != 0 || topHide.toInt() != 0) {
            return@Runnable
        }
        hideSysBar()
        val params = top.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = if (isPhonePortrait()) 0 else AutoSizeUtils.mm2px(this@HomeActivity, 10.0f)
        params.height = if (isPhonePortrait()) MobileSheets.dp(this@HomeActivity, 52f) else AutoSizeUtils.mm2px(this@HomeActivity, 50.0f)
        top.layoutParams = params
        top.alpha = 1.0f
        refreshTopInfoTextSize()
        top.requestLayout()
    }

    override fun getLayoutResID(): Int {
        return R.layout.activity_home
    }

    override fun shouldRefreshAutoSize(): Boolean {
        return true
    }

    var useCacheConfig = false

    override fun init() {
        EventBus.getDefault().register(this)
        eventBusRegistered = true
        ControlManager.get().startServer()
        initView()
        initViewModel()
        useCacheConfig = false
        val intent = intent
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            useCacheConfig = bundle?.getBoolean("useCache", false) ?: false
        }
        initData()
    }

    private fun initView() {
        this.topLayout = findViewById(R.id.topLayout)
        this.tvDate = findViewById(R.id.tvDate)
        this.tvName = findViewById(R.id.tvName)
        this.contentLayout = findViewById(R.id.contentLayout)
        this.mGridView = findViewById(R.id.mGridView)
        this.mViewPager = findViewById(R.id.mViewPager)
        val grid = mGridView
        val pager = mViewPager
        val content = contentLayout
        if (isPhonePortrait() && grid != null && pager != null && content != null) {
            content.removeView(grid)
            content.removeView(pager)
            val scroll = MobileHomeScroll(this, grid, pager)
            mobileScroll = scroll
            content.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        }
        val adapter = SortAdapter()
        this.sortAdapter = adapter
        mViewPager?.setSwipeEnabled(true)
        mViewPager?.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                mobileScroll?.selectPage(position)
                currentSelected = position
                sortFocused = position
                sortChange = false
                sortAdapter?.setSelectedPosition(position)
                mGridView?.smoothScrollToPosition(position)
            }
        })
        mGridView?.layoutManager = V7LinearLayoutManager(this.mContext, 0, false)
        mGridView?.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f))
        mGridView?.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                mGridView?.post {
                    val lm = mGridView?.layoutManager
                    val firstChild = lm?.findViewByPosition(0)
                    if (firstChild != null) {
                        mGridView?.setSelectedPosition(0)
                        firstChild.requestFocus()
                    }
                }
            }
        })
        mGridView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(tvRecyclerView: TvRecyclerView, view: View?, position: Int) {
                if (isPhonePortrait()) return
                if (view != null && !this@HomeActivity.isDownOrUp) {
                    val p = position
                    mHandler.postDelayed({
                        val textView = view.findViewById<TextView>(R.id.tvTitle) ?: return@postDelayed
                        textView.paint.isFakeBoldText = false
                        if (sortFocused == p) {
                            view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(BounceInterpolator()).setDuration(300).start()
                            textView.setTextColor(resources.getColor(R.color.color_FFFFFF))
                        } else {
                            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                            textView.setTextColor(resources.getColor(R.color.color_BBFFFFFF))
                            view.findViewById<View>(R.id.tvFilter)?.visibility = View.GONE
                            view.findViewById<View>(R.id.tvFilterColor)?.visibility = View.GONE
                        }
                        textView.invalidate()
                    }, 10)
                }
            }

            override fun onItemSelected(tvRecyclerView: TvRecyclerView, view: View?, position: Int) {
                if (isPhonePortrait()) return
                if (view != null) {
                    this@HomeActivity.currentView = view
                    this@HomeActivity.isDownOrUp = false
                    this@HomeActivity.sortChange = true
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(BounceInterpolator()).setDuration(300).start()
                    val textView = view.findViewById<TextView>(R.id.tvTitle)
                    if (textView != null) {
                        textView.paint.isFakeBoldText = true
                        textView.setTextColor(resources.getColor(R.color.color_FFFFFF))
                        textView.invalidate()
                    }
                    val sortData = sortAdapter?.getItem(position)
                    if (sortData != null && sortData.filters.isNotEmpty()) {
                        showFilterIcon(sortData.filterSelectCount())
                    }
                    this@HomeActivity.sortFocusView = view
                    this@HomeActivity.sortFocused = position
                    mHandler.removeCallbacks(mDataRunnable)
                    mHandler.postDelayed(mDataRunnable, 200)
                }
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (isPhonePortrait() && itemView != null && currentSelected != position) {
                    sortFocused = position
                    sortChange = true
                    mHandler.removeCallbacks(mDataRunnable)
                    mDataRunnable.run()
                    return
                }
                if (itemView != null && currentSelected == position) {
                    val baseLazyFragment = fragments.getOrNull(currentSelected)
                    val sData = sortAdapter?.getItem(position)
                    if (baseLazyFragment is GridFragment && sData != null && sData.filters.isNotEmpty()) {
                        baseLazyFragment.showFilter()
                    } else if (baseLazyFragment is UserFragment) {
                        showSiteSwitch()
                    }
                }
            }
        })

        mGridView?.setOnInBorderKeyEventListener { direction, _ ->
            if (direction == View.FOCUS_UP) {
                val baseLazyFragment = fragments.getOrNull(sortFocused)
                if (baseLazyFragment is UserFragment) {
                    refreshHomeSort()
                    return@setOnInBorderKeyEventListener true
                }
                if (baseLazyFragment is GridFragment) {
                    baseLazyFragment.forceRefresh()
                    return@setOnInBorderKeyEventListener true
                }
            }
            if (direction != View.FOCUS_DOWN) {
                return@setOnInBorderKeyEventListener false
            }
            val baseLazyFragment = fragments.getOrNull(sortFocused)
            if (baseLazyFragment !is GridFragment) {
                return@setOnInBorderKeyEventListener false
            }
            !baseLazyFragment.isLoad()
        }
        tvName?.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            if (isPhonePortrait() && dataInitOk && jarInitOk) {
                showSiteSwitch()
                return@setOnClickListener
            }
            if (dataInitOk && jarInitOk) {
                val jar = ApiConfig.get().getHomeSourceBean()?.getJar() ?: ""
                val spider = ApiConfig.get().getSpider() ?: ""
                val jarUrl = if (jar.isNotEmpty()) jar else spider
                val jarSource = jarUrl.split(";md5;".toRegex()).toTypedArray()[0]
                val cspCacheDir = File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar")
                val jarCacheDir = File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar")
                val jarFullCacheDir = File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar")
                Toast.makeText(mContext, "缓存已清除", Toast.LENGTH_LONG).show()
                Thread {
                    try {
                        FileUtils.deleteFile(cspCacheDir)
                        FileUtils.deleteFile(jarCacheDir)
                        FileUtils.deleteFile(jarFullCacheDir)
                        FileUtils.clearSpiderCacheFiles()
                        ApiConfig.get().clearSpiderCache()
                        refreshHome()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            } else {
                jumpActivity(SettingActivity::class.java)
            }
        }
        tvName?.setOnLongClickListener {
            jumpActivity(SettingActivity::class.java)
            true
        }
        contentLayout?.let { setLoadSir(it) }
    }

    private var skipNextUpdate = false

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel?.sortResult?.observe(this, Observer { absXml ->
            if (skipNextUpdate) {
                skipNextUpdate = false
                return@Observer
            }
            if (!homeSortLoading && loadingSourceKey == null) {
                return@Observer
            }
            if (absXml != null && absXml.sourceKey != null && loadingSourceKey != null && loadingSourceKey != absXml.sourceKey) {
                return@Observer
            }
            val home = ApiConfig.get().getHomeSourceBean()
            showSuccess()
            clearHomePages()
            val homeKey = home?.key ?: ""
            val sortList = absXml?.classes?.sortList
            val newSortData: List<MovieSort.SortData> = if (sortList != null) {
                DefaultConfig.adjustSort(homeKey, sortList, true)
            } else {
                DefaultConfig.adjustSort(homeKey, ArrayList(), true)
            }
            updateSortData(newSortData)
            initViewPager(absXml)
            updateHomeRec(absXml)
            if (home != null && !home.name.isNullOrEmpty()) {
                tvName?.text = home.name
            }
            tvName?.clearAnimation()
            homeSortLoading = false
            loadingSourceKey = null
            previousHomeName = null
            previousHomeSource = null
        })
    }

    private var dataInitOk = false
    private var jarInitOk = false
    private var searchSpiderWarmStarted = false
    private var mConfigErrorDialog: TipDialog? = null

    private fun initData() {
        if (dataInitOk && jarInitOk) {
            loadHomeSort(false)
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有")
            } else {
                LOG.e("无")
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity::class.java)
            }
            if (!useCacheConfig) warmSearchSpidersOnce()
            return
        }
        tvNameAnimation()
        showLoading()
        if (dataInitOk && !jarInitOk) {
            val spider = ApiConfig.get().getSpider() ?: ""
            if (spider.isNotEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, spider, object : ApiConfig.LoadConfigCallback {
                    override fun success() {
                        jarInitOk = true
                        mHandler.postDelayed({
                            initData()
                        }, 50)
                    }

                    override fun notice(msg: String) {
                        mHandler.post {
                            Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun error(msg: String) {
                        jarInitOk = true
                        dataInitOk = true
                        mHandler.postDelayed({
                            Toast.makeText(this@HomeActivity, "$msg jar load err", Toast.LENGTH_SHORT).show()
                            initData()
                        }, 50)
                    }
                })
            }
            return
        }
        ApiConfig.get().loadConfig(useCacheConfig, object : ApiConfig.LoadConfigCallback {
            override fun notice(msg: String) {
                mHandler.post {
                    Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun success() {
                dataInitOk = true
                val spider = ApiConfig.get().getSpider() ?: ""
                if (spider.isEmpty()) {
                    jarInitOk = true
                }
                mHandler.postDelayed({
                    initData()
                }, 50)
            }

            override fun error(msg: String) {
                if (msg.equals("-1", ignoreCase = true)) {
                    mHandler.post {
                        dataInitOk = true
                        jarInitOk = true
                        initData()
                    }
                    return
                }
                mHandler.post {
                    if (isActivityUnavailable()) {
                        return@post
                    }
                    if (mConfigErrorDialog == null) {
                        mConfigErrorDialog = TipDialog(this@HomeActivity, msg, "重试", "取消", object : TipDialog.OnListener {
                            override fun left() {
                                mHandler.post {
                                    dismissConfigErrorDialog()
                                    initData()
                                }
                            }

                            override fun right() {
                                dataInitOk = true
                                jarInitOk = true
                                mHandler.post {
                                    dismissConfigErrorDialog()
                                    initData()
                                }
                            }

                            override fun cancel() {
                                dataInitOk = true
                                jarInitOk = true
                                mHandler.post {
                                    dismissConfigErrorDialog()
                                    initData()
                                }
                            }
                        })
                    }
                    if (mConfigErrorDialog?.isShowing == false) {
                        mConfigErrorDialog?.show()
                    }
                }
            }
        }, this)
    }

    private fun warmSearchSpidersOnce() {
        if (searchSpiderWarmStarted) return
        searchSpiderWarmStarted = true
        ApiConfig.get().warmSearchSpiders()
    }

    private fun loadHomeSort(keepCurrentContent: Boolean) {
        val home = ApiConfig.get().getHomeSourceBean()
        homeSortLoading = keepCurrentContent
        if (keepCurrentContent && home != null && !home.name.isNullOrEmpty()) {
            previousHomeName = tvName?.text?.toString()
            tvName?.text = home.name
        }
        tvNameAnimation()
        if (home == null) {
            loadingSourceKey = null
            if (!keepCurrentContent) showLoading()
            sourceViewModel?.getSort(null)
            return
        }
        loadingSourceKey = home.key
        if (!keepCurrentContent) {
            showLoading()
        }
        sourceViewModel?.getSort(loadingSourceKey)
    }

    private fun initViewPager(absXml: AbsSortXml?) {
        val dataList = sortAdapter?.data
        if (dataList != null && dataList.isNotEmpty()) {
            for (data in dataList) {
                if (data.id == "my0") {
                    val vList = absXml?.videoList
                    if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && !vList.isNullOrEmpty()) {
                        fragments.add(UserFragment.newInstance(vList))
                    } else {
                        fragments.add(UserFragment.newInstance(null))
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data))
                }
            }
            val pAdapter = HomePageAdapter(supportFragmentManager, fragments)
            pageAdapter = pAdapter
            try {
                val field = ViewPager::class.java.getDeclaredField("mScroller")
                field.isAccessible = true
                val scroller = FixedSpeedScroller(mContext, AccelerateInterpolator())
                field.set(mViewPager, scroller)
                scroller.setmDuration(300)
            } catch (e: Exception) {
            }
            mViewPager?.setPageTransformer(true, DefaultTransformer())
            mViewPager?.adapter = pAdapter
            mViewPager?.setCurrentItem(currentSelected, false)
            sortAdapter?.setSelectedPosition(currentSelected)
        }
    }

    private fun clearHomePages() {
        mobileScroll?.selectPage(0)
        mHandler.removeCallbacks(mDataRunnable)
        currentSelected = 0
        sortFocused = 0
        sortChange = false
        sortFocusView = null
        currentView = null
        if (pageAdapter != null) {
            mViewPager?.adapter = null
            pageAdapter?.removeAll()
            pageAdapter = null
        } else if (fragments.isNotEmpty()) {
            fragments.clear()
        }
    }

    private fun updateSortData(newSortData: List<MovieSort.SortData>?) {
        val newData = newSortData ?: ArrayList()
        val oldSortData = sortAdapter?.data ?: ArrayList()
        if (oldSortData.isEmpty()
            || newData.isEmpty()
            || oldSortData[0] == null
            || newData[0] == null
            || "my0" != oldSortData[0].id
            || "my0" != newData[0].id
        ) {
            sortAdapter?.setNewData(newData)
            return
        }
        val oldTailCount = oldSortData.size - 1
        if (oldTailCount > 0) {
            oldSortData.subList(1, oldSortData.size).clear()
            sortAdapter?.notifyItemRangeRemoved(1, oldTailCount)
        }
        if (newData.size > 1) {
            oldSortData.addAll(newData.subList(1, newData.size))
            sortAdapter?.notifyItemRangeInserted(1, newData.size - 1)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBackPressed() {
        if (homeSortLoading) {
            cancelHomeSortLoading()
            return
        }
        if (isLoading()) {
            refreshEmpty()
            return
        }
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false
            UserFragment.homeHotVodAdapter?.notifyDataSetChanged()
            return
        }

        if (this.fragments.isEmpty() || this.sortFocused >= this.fragments.size || this.sortFocused < 0) {
            doExit()
            return
        }

        val baseLazyFragment = this.fragments[this.sortFocused]
        if (baseLazyFragment is GridFragment) {
            if (baseLazyFragment.restoreView()) {
                return
            }
            val focusView = this.sortFocusView
            if (focusView != null && !focusView.isFocused) {
                focusView.requestFocus()
            } else if (this.sortFocused != 0) {
                this.mGridView?.setSelection(0)
            } else {
                doExit()
            }
        } else if (baseLazyFragment is UserFragment && UserFragment.tvHotList?.canScrollVertically(-1) == true) {
            UserFragment.tvHotList?.scrollToPosition(0)
            mobileScroll?.expand()
            this.mGridView?.setSelection(0)
        } else {
            doExit()
        }
    }

    private fun doExit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity()
            unregisterEventBus()
            ControlManager.get().stopServer()
            finish()
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        } else {
            mExitTime = System.currentTimeMillis()
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTopInfoTextSize()
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable)
        mHandler.postDelayed(refreshTopInfoTextSizeRunnable, 350)
        mHandler.removeCallbacks(refreshTopLayoutRunnable)
        mHandler.postDelayed(refreshTopLayoutRunnable, 450)
        mHandler.post(mRunnable)
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable)
        mHandler.removeCallbacks(refreshTopLayoutRunnable)
        mHandler.removeCallbacks(mRunnable)
    }

    private fun refreshTopInfoTextSize() {
        if (tvName == null || tvDate == null) {
            return
        }
        if (isPhonePortrait()) {
            tvName?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            tvDate?.visibility = View.GONE
            return
        }
        tvName?.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ts_30))
        tvDate?.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ts_26))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                val newIntent = Intent(mContext, DetailActivity::class.java).apply {
                    putExtra("id", event.obj as? String)
                    putExtra("sourceKey", "push_agent")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                this@HomeActivity.startActivity(newIntent)
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon(event.obj as Int)
            }
        } else if (event.type == RefreshEvent.TYPE_HOME_SOURCE_CHANGE) {
            refreshHome(false)
        } else if (event.type == RefreshEvent.TYPE_CONFIG_AUTH_CHANGED) {
            refreshHomeSort()
            for (fragment in fragments) {
                if (fragment is GridFragment && fragment.isLoad()) {
                    fragment.forceRefresh()
                }
            }
        }
    }

    private fun showFilterIcon(count: Int) {
        val visible = count > 0
        currentView?.findViewById<View>(R.id.tvFilterColor)?.visibility = if (visible) View.VISIBLE else View.GONE
        currentView?.findViewById<View>(R.id.tvFilter)?.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private val mDataRunnable = Runnable {
        if (sortChange) {
            sortChange = false
            val baseLazyFragment = fragments.getOrNull(sortFocused)
            if (sortFocused != currentSelected) {
                currentSelected = sortFocused
                mViewPager?.setCurrentItem(sortFocused, false)
                changeTop(sortFocused != 0)
                if (baseLazyFragment is GridFragment && baseLazyFragment.shouldReloadOnSelect()) {
                    baseLazyFragment.forceRefresh()
                }
            } else if (baseLazyFragment is GridFragment && baseLazyFragment.shouldReloadOnSelect()) {
                baseLazyFragment.forceRefresh()
            }
        }
    }

    private var menuKeyDownTime: Long = 0

    companion object {
        private const val LONG_PRESS_THRESHOLD = 2000L
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (topHide < 0) return false
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis()
            } else if (event.action == KeyEvent.ACTION_UP) {
                val pressDuration = System.currentTimeMillis() - menuKeyDownTime
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity::class.java)
                } else {
                    showSiteSwitch()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    var topHide: Byte = 0

    private fun changeTop(hide: Boolean) {
        if (isPhonePortrait()) return
        val top = topLayout ?: return
        val viewObj = ViewObj(top, top.layoutParams as ViewGroup.MarginLayoutParams)
        val animatorSet = AnimatorSet()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                topHide = (if (hide) 1 else 0).toByte()
            }

            override fun onAnimationCancel(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        })
        if (hide && topHide.toInt() == 0) {
            animatorSet.playTogether(
                ObjectAnimator.ofObject(
                    viewObj, "marginTop", IntEvaluator(),
                    AutoSizeUtils.mm2px(this.mContext, 10.0f),
                    AutoSizeUtils.mm2px(this.mContext, 0.0f)
                ),
                ObjectAnimator.ofObject(
                    viewObj, "height", IntEvaluator(),
                    AutoSizeUtils.mm2px(this.mContext, 50.0f),
                    AutoSizeUtils.mm2px(this.mContext, 1.0f)
                ),
                ObjectAnimator.ofFloat(top, "alpha", 1.0f, 0.0f)
            )
            animatorSet.duration = 200
            animatorSet.start()
            return
        }
        if (!hide && topHide.toInt() == 1) {
            animatorSet.playTogether(
                ObjectAnimator.ofObject(
                    viewObj, "marginTop", IntEvaluator(),
                    AutoSizeUtils.mm2px(this.mContext, 0.0f),
                    AutoSizeUtils.mm2px(this.mContext, 10.0f)
                ),
                ObjectAnimator.ofObject(
                    viewObj, "height", IntEvaluator(),
                    AutoSizeUtils.mm2px(this.mContext, 1.0f),
                    AutoSizeUtils.mm2px(this.mContext, 50.0f)
                ),
                ObjectAnimator.ofFloat(top, "alpha", 0.0f, 1.0f)
            )
            animatorSet.duration = 200
            animatorSet.start()
        }
    }

    override fun onDestroy() {
        dismissHomeDialogs()
        mHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        unregisterEventBus()
        if (isFinishing) {
            ControlManager.get().stopServer()
        }
    }

    private fun unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this)
            eventBusRegistered = false
        }
    }

    private var mSiteSwitchDialog: SelectDialog<SourceBean>? = null

    fun showSiteSwitch() {
        if (isActivityUnavailable()) return
        val sites = ApiConfig.get().getSwitchSourceBeanList()
        if (sites.isEmpty()) return
        var select = sites.indexOf(ApiConfig.get().getHomeSourceBean())
        if (select < 0 || select >= sites.size) select = 0
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = SelectDialog<SourceBean>(this@HomeActivity)
            val tvRecyclerView: TvRecyclerView? = mSiteSwitchDialog?.findViewById(R.id.list)
            var spanCount = Math.floor(sites.size / 20.0).toInt()
            spanCount = Math.min(spanCount, 2)
            if (isPhonePortrait()) spanCount = Math.min(spanCount, 1)
            tvRecyclerView?.layoutManager = V7GridLayoutManager(mSiteSwitchDialog?.context ?: this, spanCount + 1)
            val clRoot: ConstraintLayout? = mSiteSwitchDialog?.findViewById(R.id.cl_root)
            val clp = clRoot?.layoutParams
            if (clp != null) {
                clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog?.context ?: this, 380f + 200f * spanCount)
                if (isPhonePortrait()) clp.width = ViewGroup.LayoutParams.MATCH_PARENT
                clRoot.layoutParams = clp
            }
            mSiteSwitchDialog?.setTip("请选择首页数据源")
        }
        mSiteSwitchDialog?.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<SourceBean> {
            override fun click(value: SourceBean, pos: Int) {
                dismissSiteSwitchDialog()
                previousHomeSource = ApiConfig.get().getHomeSourceBean()
                ApiConfig.get().setSourceBean(value)
                refreshHome(false)
            }

            override fun getDisplay(valItem: SourceBean): String {
                return valItem.name ?: ""
            }
        }, object : DiffUtil.ItemCallback<SourceBean>() {
            override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                return oldItem.key == newItem.key
            }
        }, sites, select)
        if (mSiteSwitchDialog?.isShowing == false) {
            mSiteSwitchDialog?.show()
        }
    }

    private fun refreshHome() {
        refreshHome(true)
    }

    private fun refreshHomeSort() {
        refreshHomeRec = true
        UserFragment.homeHotVodAdapter?.setNewData(ArrayList())
        val home = ApiConfig.get().getHomeSourceBean()
        if (home != null) {
            SourceViewModel.clearSortCache(home.key)
        }
        refreshHome(false)
    }

    private fun updateHomeRec(absXml: AbsSortXml?) {
        if (!refreshHomeRec) return
        refreshHomeRec = false
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) != 1) return
        val list = absXml?.videoList
        if (list == null || UserFragment.homeHotVodAdapter == null) return
        UserFragment.homeHotVodAdapter?.setNewData(list)
    }

    private fun refreshHome(restart: Boolean) {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            mHandler.post {
                refreshHome(restart)
            }
            return
        }
        if (isActivityUnavailable()) {
            return
        }
        dismissHomeDialogs()
        if (!restart) {
            loadHomeSort(true)
            return
        }
        val intent = Intent(applicationContext, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val bundle = Bundle()
            bundle.putBoolean("useCache", true)
            putExtras(bundle)
        }
        this@HomeActivity.startActivity(intent)
    }

    private fun isActivityUnavailable(): Boolean {
        return isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
    }

    private fun dismissHomeDialogs() {
        dismissConfigErrorDialog()
        dismissSiteSwitchDialog()
    }

    private fun dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog?.isShowing == true) {
                mConfigErrorDialog?.dismiss()
            }
            mConfigErrorDialog = null
        }
    }

    private fun dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog?.isShowing == true) {
                mSiteSwitchDialog?.dismiss()
            }
            mSiteSwitchDialog = null
        }
    }

    private fun refreshEmpty() {
        skipNextUpdate = true
        showSuccess()
        cancelHomeSortLoading()
        clearHomePages()
        val homeKey = ApiConfig.get().getHomeSourceBean()?.key ?: ""
        sortAdapter?.setNewData(DefaultConfig.adjustSort(homeKey, ArrayList(), true))
        initViewPager(null)
        tvName?.clearAnimation()
    }

    private fun cancelHomeSortLoading() {
        homeSortLoading = false
        loadingSourceKey = null
        tvName?.clearAnimation()
        val prev = previousHomeSource
        if (prev != null) {
            ApiConfig.get().setSourceBean(prev)
        }
        if (!previousHomeName.isNullOrEmpty()) {
            tvName?.text = previousHomeName
        }
        previousHomeSource = null
        previousHomeName = null
    }

    private fun tvNameAnimation() {
        tvName?.clearAnimation()
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 500
            startOffset = 20
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        tvName?.startAnimation(blinkAnimation)
    }
}
