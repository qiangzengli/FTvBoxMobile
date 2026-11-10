package com.github.tvbox.osc.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.ui.adapter.SettingMenuAdapter
import com.github.tvbox.osc.ui.adapter.SettingPageAdapter
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment
import com.github.tvbox.osc.util.AppManager
import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import java.util.ArrayList

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
class SettingActivity : BaseActivity() {

    fun interface DevModeCallback {
        fun onChange()
    }

    companion object {
        @JvmField
        var callback: DevModeCallback? = null
    }

    private var mGridView: TvRecyclerView? = null
    private var mViewPager: ViewPager? = null
    private var sortAdapter: SettingMenuAdapter? = null
    private var pageAdapter: SettingPageAdapter? = null
    private val fragments: MutableList<BaseLazyFragment> = ArrayList()
    private var sortChange = false
    private var defaultSelected = 0
    private var sortFocused = 0
    private val mHandler = Handler(Looper.getMainLooper())
    private var homeSourceKey: String? = null
    private var currentApi: String? = null
    private var homeRec = 0
    private var currentLiveApi: String? = null
    var devMode: String = ""

    override fun getLayoutResID(): Int {
        return R.layout.activity_setting
    }

    override fun shouldRefreshAutoSize(): Boolean {
        return true
    }

    override fun init() {
        initView()
        initData()
    }

    private fun initView() {
        mGridView = findViewById(R.id.mGridView)
        mViewPager = findViewById(R.id.mViewPager)
        sortAdapter = SettingMenuAdapter()
        mGridView?.adapter = sortAdapter
        mGridView?.layoutManager = V7LinearLayoutManager(this.mContext, 1, false)
        sortAdapter?.onItemChildClickListener = BaseQuickAdapter.OnItemChildClickListener { _, view, position ->
            if (view.id == R.id.tvName) {
                if (view.parent != null) {
                    (view.parent as ViewGroup).requestFocus()
                    sortFocused = position
                    if (sortFocused != defaultSelected) {
                        defaultSelected = sortFocused
                        mViewPager?.setCurrentItem(sortFocused, false)
                    }
                }
            }
        }
        mGridView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (itemView != null) {
                    val tvName = itemView.findViewById<TextView>(R.id.tvName)
                    tvName?.setTextColor(resources.getColor(R.color.color_CCFFFFFF))
                }
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View?, position: Int) {
                if (itemView != null) {
                    sortChange = true
                    sortFocused = position
                    val tvName = itemView.findViewById<TextView>(R.id.tvName)
                    tvName?.setTextColor(Color.WHITE)
                }
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {}
        })
    }

    private fun initData() {
        currentApi = Hawk.get(HawkConfig.API_URL, "")
        homeSourceKey = ApiConfig.get().getHomeSourceBean()?.getKey()
        homeRec = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)
        currentLiveApi = Hawk.get(HawkConfig.LIVE_API_URL, "")
        val sortList = ArrayList<String>()
        sortList.add("设置其他")
        sortAdapter?.setNewData(sortList)
        initViewPager()
    }

    private fun initViewPager() {
        fragments.add(ModelSettingFragment.newInstance())
        pageAdapter = SettingPageAdapter(supportFragmentManager, fragments)
        mViewPager?.adapter = pageAdapter
        mViewPager?.currentItem = 0
    }

    private val mDataRunnable = Runnable {
        if (sortChange) {
            sortChange = false
            if (sortFocused != defaultSelected) {
                defaultSelected = sortFocused
                mViewPager?.setCurrentItem(sortFocused, false)
            }
        }
    }

    private val mDevModeRun = Runnable {
        devMode = ""
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            mHandler.removeCallbacks(mDataRunnable)
            val keyCode = event.keyCode
            when (keyCode) {
                KeyEvent.KEYCODE_0 -> {
                    mHandler.removeCallbacks(mDevModeRun)
                    devMode += "0"
                    mHandler.postDelayed(mDevModeRun, 200)
                    if (devMode.length >= 4) {
                        callback?.onChange()
                    }
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            mHandler.postDelayed(mDataRunnable, 200)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (currentApi == Hawk.get(HawkConfig.API_URL, "")) {
            if (homeRec != Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)) {
                jumpActivity(HomeActivity::class.java, createBundle())
            } else if (currentLiveApi != Hawk.get(HawkConfig.LIVE_API_URL, "")) {
                jumpActivity(HomeActivity::class.java, createBundle())
            }
        } else {
            AppManager.getInstance().finishActivity(HomeActivity::class.java)
            jumpActivity(HomeActivity::class.java)
            finish()
            return
        }
        super.onBackPressed()
    }

    private fun createBundle(): Bundle {
        val bundle = Bundle()
        bundle.putBoolean("useCache", true)
        return bundle
    }
}
