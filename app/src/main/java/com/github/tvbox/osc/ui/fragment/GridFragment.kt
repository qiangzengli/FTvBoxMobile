package com.github.tvbox.osc.ui.fragment

import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.github.tvbox.osc.ui.adapter.GridAdapter
import com.github.tvbox.osc.ui.dialog.GridFilterDialog
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import java.util.Stack

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
class GridFragment : BaseLazyFragment() {
    private var sortData: MovieSort.SortData? = null
    private var mGridView: TvRecyclerView? = null
    private var sourceViewModel: SourceViewModel? = null
    private var gridFilterDialog: GridFilterDialog? = null
    private var gridAdapter: GridAdapter? = null
    private var page = 1
    private var maxPage = 1
    private var isLoad = false
    private var isRequesting = false
    private var hasActionItems = false
    private var isTop = true
    private var focusedView: View? = null
    private var pullRefreshStartX = 0f
    private var pullRefreshStartY = 0f
    private var pullRefreshStartAtTop = false
    private var pullRefreshReady = false
    private var pullRefreshThreshold = 0
    private var loadSirView: View? = null
    private var style: ImgUtil.Style? = null

    private class GridInfo {
        var sortID: String = ""
        var mGridView: TvRecyclerView? = null
        var gridAdapter: GridAdapter? = null
        var page = 1
        var maxPage = 1
        var isLoad = false
        var hasActionItems = false
        var focusedView: View? = null
    }

    private val mGrids = Stack<GridInfo>() // ui栈

    companion object {
        @JvmStatic
        fun newInstance(sortData: MovieSort.SortData?): GridFragment {
            return GridFragment().setArguments(sortData)
        }
    }

    fun setArguments(sortData: MovieSort.SortData?): GridFragment {
        this.sortData = sortData
        return this
    }

    override fun getLayoutResID(): Int {
        return R.layout.fragment_grid
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gridView: TvRecyclerView? = view.findViewById(R.id.mGridView)
        if (gridView != null && gridView.layoutManager == null) {
            val ctx = context ?: return
            gridView.layoutManager = V7LinearLayoutManager(ctx, 1, false)
        }
    }

    override fun init() {
        initView()
        initViewModel()
        initData()
    }

    private fun changeView(id: String, isFolder: Boolean) {
        val currentSort = sortData ?: return
        if (isFolder) {
            currentSort.flag = if (style == null) "1" else "2"
        } else {
            currentSort.flag = "2"
        }
        initView()
        currentSort.id = id
        initViewModel()
        initData()
    }

    fun isFolederMode(): Boolean {
        return getUITag() == '1'
    }

    // 获取当前页面UI的显示模式 ‘0’ 正常模式 '1' 文件夹模式 '2' 显示缩略图的文件夹模式
    fun getUITag(): Char {
        val flag = sortData?.flag
        return if (sortData == null || flag == null || flag.isEmpty() || style != null) '0' else flag[0]
    }

    // 是否允许聚合搜索 sortData.flag的第二个字符为‘1’时允许聚搜
    fun enableFastSearch(): Boolean {
        val flag = sortData?.flag
        return flag == null || flag.length < 2 || flag[1] == '1'
    }

    // 保存当前页面
    private fun saveCurrentView() {
        val currentView = this.mGridView ?: return
        val currentSort = this.sortData ?: return
        val info = GridInfo().apply {
            sortID = currentSort.id ?: ""
            mGridView = currentView
            gridAdapter = this@GridFragment.gridAdapter
            page = this@GridFragment.page
            maxPage = this@GridFragment.maxPage
            isLoad = this@GridFragment.isLoad
            hasActionItems = this@GridFragment.hasActionItems
            focusedView = this@GridFragment.focusedView
        }
        mGrids.push(info)
    }

    // 丢弃当前页面，将页面还原成上一个保存的页面
    fun restoreView(): Boolean {
        if (mGrids.empty()) return false
        showSuccess()
        val currentView = mGridView
        if (currentView != null && currentView.parent is ViewGroup) {
            (currentView.parent as ViewGroup).removeView(currentView)
        }
        val info = mGrids.pop()
        sortData?.id = info.sortID
        mGridView = info.mGridView
        gridAdapter = info.gridAdapter
        page = info.page
        maxPage = info.maxPage
        isLoad = info.isLoad
        hasActionItems = info.hasActionItems
        focusedView = info.focusedView
        mGridView?.visibility = View.VISIBLE
        mGridView?.requestFocus()
        return true
    }

    // 更改当前页面
    private fun createView() {
        saveCurrentView()
        val ctx = context ?: requireContext()
        val currentView = mGridView
        if (currentView == null) {
            mGridView = findViewById(R.id.mGridView)
        } else {
            val v3 = TvRecyclerView(ctx)
            v3.setSpacingWithMargins(10, 10)
            v3.layoutParams = currentView.layoutParams
            v3.setPadding(
                currentView.paddingLeft,
                currentView.paddingTop,
                currentView.paddingRight,
                currentView.paddingBottom
            )
            v3.clipToPadding = currentView.clipToPadding
            (currentView.parent as? ViewGroup)?.addView(v3)
            currentView.visibility = View.GONE
            mGridView = v3
            mGridView?.visibility = View.VISIBLE
        }
        mGridView?.setHasFixedSize(true)
        style = ImgUtil.initStyle()
        gridAdapter = GridAdapter(isFolederMode(), style)
        page = 1
        maxPage = 1
        isLoad = false
    }

    private fun initView() {
        createView()
        val grid = mGridView ?: return
        val ctx = context ?: requireContext()
        if (isFolederMode()) {
            grid.layoutManager = V7LinearLayoutManager(ctx, 1, false)
        } else {
            var spanCount = if (isBaseOnWidth) 5 else 6
            if (style != null) {
                spanCount = ImgUtil.spanCountByStyle(style, spanCount)
            }
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                spanCount = Math.min(3, spanCount)
            }
            if (spanCount == 1) {
                grid.layoutManager = V7LinearLayoutManager(ctx, spanCount, false)
            } else {
                grid.layoutManager = V7GridLayoutManager(ctx, spanCount)
            }
        }
        grid.adapter = gridAdapter

        gridAdapter?.setOnLoadMoreListener(BaseQuickAdapter.RequestLoadMoreListener {
            gridAdapter?.setEnableLoadMore(true)
            sourceViewModel?.getList(sortData, page)
        }, grid)

        grid.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
            }
        })

        grid.setOnInBorderKeyEventListener { direction, _ ->
            if (direction == View.FOCUS_UP) {
            }
            false
        }

        gridAdapter?.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = gridAdapter?.data?.getOrNull(position)
            if (video != null) {
                val vid = video.id
                val skey = video.sourceKey ?: ""
                val vaction = video.action
                if (vaction != null) {
                    if (com.fbox.mobile.auth.MobileConfigAuth.handle(this, video) { forceRefresh() }) return@setOnItemClickListener
                    sourceViewModel?.action(skey, vaction)
                    return@setOnItemClickListener
                }
                val bundle = Bundle().apply {
                    putString("id", vid)
                    putString("sourceKey", skey)
                    putString("title", video.name)
                }
                if (video.tag != null && (video.tag == "folder" || video.tag == "cover")) {
                    focusedView = view
                    val targetId = vid ?: ""
                    if ("12".indexOf(getUITag()) != -1) {
                        changeView(targetId, video.tag == "folder")
                    } else {
                        changeView(targetId, false)
                    }
                } else {
                    if (vid != null && vid.startsWith("msearch:")) {
                        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true) && enableFastSearch()) {
                            jumpActivity(FastSearchActivity::class.java, bundle)
                        } else {
                            jumpActivity(SearchActivity::class.java, bundle)
                        }
                    } else {
                        bundle.putString("picture", video.pic)
                        jumpActivity(DetailActivity::class.java, bundle)
                    }
                }
            }
        }

        gridAdapter?.setOnItemLongClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = gridAdapter?.data?.getOrNull(position)
            if (video != null) {
                val vid = video.id
                val bundle = Bundle().apply {
                    putString("id", vid)
                    putString("sourceKey", video.sourceKey)
                    putString("title", video.name)
                }
                if (vid != null && vid.startsWith("msearch:")) {
                    bundle.putString("picture", video.pic)
                    jumpActivity(DetailActivity::class.java, bundle)
                } else {
                    jumpActivity(FastSearchActivity::class.java, bundle)
                }
            }
            true
        }

        gridAdapter?.setLoadMoreView(LoadMoreView())
        setLoadSir2(grid)
        initPullRefresh()
    }

    private fun initPullRefresh() {
        val grid = mGridView ?: return
        val ctx = context ?: requireContext()
        pullRefreshThreshold = ViewConfiguration.get(ctx).scaledTouchSlop * 6
        grid.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                return handlePullRefreshTouch(rv, event)
            }

            override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            }
        })
        loadSirView = grid.parent as? View
        loadSirView?.setOnTouchListener { v, event ->
            handlePullRefreshTouch(v, event)
        }
    }

    private fun bindPullRefreshTouch(view: View?) {
        if (view == null || view == mGridView) return
        view.setOnTouchListener { v, event ->
            handlePullRefreshTouch(v, event)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                bindPullRefreshTouch(view.getChildAt(i))
            }
        }
    }

    private fun handlePullRefreshTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullRefreshStartX = event.x
                pullRefreshStartY = event.y
                pullRefreshStartAtTop = !view.canScrollVertically(-1)
                pullRefreshReady = false
            }
            MotionEvent.ACTION_MOVE -> {
                val diffX = Math.abs(event.x - pullRefreshStartX)
                val diffY = event.y - pullRefreshStartY
                pullRefreshReady = pullRefreshStartAtTop && diffY > pullRefreshThreshold && diffY > diffX
            }
            MotionEvent.ACTION_UP -> {
                if (pullRefreshReady) {
                    pullRefreshReady = false
                    forceRefresh()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pullRefreshReady = false
            }
        }
        return false
    }

    override fun showEmpty() {
        super.showEmpty()
        bindPullRefreshTouch(loadSirView)
    }

    private fun initViewModel() {
        if (sourceViewModel != null) return
        val vm = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel = vm
        vm.listResult.observe(this, Observer { absXml ->
            isRequesting = false
            val adapter = gridAdapter ?: return@Observer
            val movie = absXml?.movie
            val videoList = movie?.videoList
            if (absXml != null && movie != null && videoList != null && videoList.isNotEmpty()) {
                if (page == 1) {
                    showSuccess()
                    isLoad = true
                    hasActionItems = hasActionVideo(videoList)
                    adapter.setNewData(videoList)
                } else {
                    hasActionItems = hasActionItems || hasActionVideo(videoList)
                    adapter.addData(videoList)
                }
                page++
                maxPage = movie.pagecount
                if (maxPage > 0 && page > maxPage) {
                    adapter.loadMoreEnd()
                    adapter.setEnableLoadMore(false)
                    if (page > 2) Toast.makeText(context, "没有更多了", Toast.LENGTH_SHORT).show()
                } else {
                    adapter.loadMoreComplete()
                    adapter.setEnableLoadMore(true)
                }
            } else {
                if (page == 1) {
                    hasActionItems = false
                    showEmpty()
                } else if (page > 2) {
                    Toast.makeText(context, "没有更多了", Toast.LENGTH_SHORT).show()
                }
                adapter.loadMoreEnd()
                adapter.setEnableLoadMore(false)
            }
        })
        vm.actionResult.observe(this, Observer { jsonObject ->
            if (jsonObject == null) return@Observer
            val msg = jsonObject.optString("msg")
            if (msg.isNotEmpty()) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                forceRefresh()
            }
        })
    }

    fun isLoad(): Boolean {
        return isLoad || !mGrids.empty()
    }

    fun shouldReloadOnSelect(): Boolean {
        return !isRequesting && mGrids.empty() && (hasActionItems || !isLoad)
    }

    private fun initData() {
        showLoading()
        isRequesting = true
        isLoad = false
        hasActionItems = false
        scrollTop()
        toggleFilterColor()
        sourceViewModel?.getList(sortData, page)
    }

    private fun hasActionVideo(videos: List<Movie.Video>?): Boolean {
        if (videos == null) return false
        for (video in videos) {
            if (video != null && video.action != null) return true
        }
        return false
    }

    private fun toggleFilterColor() {
        val sort = sortData
        if (sort != null && sort.filters != null && sort.filters.isNotEmpty()) {
            val count = sort.filterSelectCount()
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_FILTER_CHANGE, count))
        }
    }

    fun isTop(): Boolean {
        return isTop
    }

    fun scrollTop() {
        isTop = true
        mGridView?.scrollToPosition(0)
    }

    fun showFilter() {
        val filters = sortData?.filters
        if (filters != null && filters.isNotEmpty() && gridFilterDialog == null) {
            gridFilterDialog = GridFilterDialog(context ?: requireContext())
            setFilterDialogData()
        }
        gridFilterDialog?.show()
    }

    fun setFilterDialogData() {
        val sort = sortData ?: return
        gridFilterDialog?.setData(sort)
        gridFilterDialog?.setOnChanged(object : GridFilterDialog.Callback {
            override fun change() {
                forceRefresh()
            }
        })
    }

    fun forceRefresh() {
        if (mGridView == null || gridAdapter == null || sourceViewModel == null) return
        if (isRequesting) return
        page = 1
        initData()
    }
}
