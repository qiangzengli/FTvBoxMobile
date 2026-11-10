package com.github.tvbox.osc.ui.dialog

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Subtitle
import com.github.tvbox.osc.ui.adapter.SearchSubtitleAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.viewmodel.SubtitleViewModel
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import java.util.ArrayList

class SearchSubtitleDialog(private val mContext: Context) : BaseDialog(mContext) {

    private lateinit var mGridView: TvRecyclerView
    private lateinit var searchAdapter: SearchSubtitleAdapter

    private lateinit var subtitleSearchBtn: TextView
    private lateinit var subtitleSearchEt: EditText
    private var mSubtitleLoader: SubtitleLoader? = null
    private lateinit var loadingBar: ProgressBar
    private var subtitleViewModel: SubtitleViewModel? = null
    private var page = 1
    private val maxPage = 5
    private var searchWord = ""

    private var zipSubtitles: MutableList<Subtitle> = ArrayList()
    private var isSearchPag = true

    init {
        if (mContext is Activity) {
            setOwnerActivity(mContext)
        }
        setContentView(R.layout.dialog_search_subtitle)
        initView(mContext)
        initViewModel()
    }

    protected fun initView(context: Context) {
        loadingBar = findViewById(R.id.loadingBar)
        mGridView = findViewById(R.id.mGridView)
        subtitleSearchEt = findViewById(R.id.input)
        subtitleSearchBtn = findViewById(R.id.inputSubmit)
        searchAdapter = SearchSubtitleAdapter()
        mGridView.setHasFixedSize(true)
        mGridView.layoutManager = V7LinearLayoutManager(getContext(), 1, false)
        mGridView.adapter = searchAdapter
        searchAdapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val subtitle = searchAdapter.data[position]
            // 加载字幕
            if (mSubtitleLoader != null) {
                if (subtitle.getIsZip()) {
                    isSearchPag = false
                    loadingBar.visibility = View.VISIBLE
                    mGridView.visibility = View.GONE
                    subtitleViewModel?.getSearchResultSubtitleUrls(subtitle)
                } else {
                    loadSubtitle(subtitle)
                    dismiss()
                }
            }
        }

        searchAdapter.setOnLoadMoreListener({
            if (searchAdapter.data.isNotEmpty() && searchAdapter.data[0].getIsZip()) {
                subtitleViewModel?.searchResult(searchWord, page)
            }
        }, mGridView)

        subtitleSearchBtn.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val wd = subtitleSearchEt.text.toString().trim()
            search(wd)
        }
        searchAdapter.setNewData(ArrayList())
    }

    fun setSearchWord(wd: String) {
        var cleanWd = wd.replace(Regex("(?:（|\\(|\\[|【|\\.mp4|\\.mkv|\\.avi|\\.MP4|\\.MKV|\\.AVI)"), "")
        cleanWd = cleanWd.replace(Regex("(?:：|\\:|）|\\)|\\]|】|\\.)"), " ")
        val len = cleanWd.length
        val finalLen = if (len >= 36) 36 else len
        cleanWd = cleanWd.substring(0, finalLen).trim()
        subtitleSearchEt.setText(cleanWd)
        subtitleSearchEt.setSelection(cleanWd.length)
        subtitleSearchEt.requestFocus()
        search(cleanWd)
    }

    fun search(wd: String) {
        com.fbox.mobile.MobileSheets.hideKeyboard(subtitleSearchEt)
        isSearchPag = true
        searchAdapter.setNewData(ArrayList())
        if (!TextUtils.isEmpty(wd)) {
            loadingBar.visibility = View.VISIBLE
            mGridView.visibility = View.GONE
            searchWord = wd
            page = 1
            subtitleViewModel?.searchResult(wd, page)
        } else {
            Toast.makeText(context, "输入内容不能为空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViewModel() {
        val vm = ViewModelProvider(mContext as ViewModelStoreOwner).get(SubtitleViewModel::class.java)
        subtitleViewModel = vm
        vm.searchResult.observe(mContext as LifecycleOwner, Observer { subtitleData ->
            if (subtitleData == null) return@Observer
            val data = subtitleData.getSubtitleList()
            loadingBar.visibility = View.GONE
            mGridView.visibility = View.VISIBLE
            if (data == null) {
                mGridView.post {
                    Toast.makeText(context, "未查询到匹配字幕", Toast.LENGTH_SHORT).show()
                }
                return@Observer
            }

            if (data.isNotEmpty()) {
                mGridView.requestFocus()
                if (subtitleData.getIsZip() == true) {
                    if (subtitleData.getIsNew() == true) {
                        searchAdapter.setNewData(data)
                        zipSubtitles = ArrayList(data)
                    } else {
                        searchAdapter.addData(data)
                        zipSubtitles.addAll(data)
                    }
                    page++
                    if (page > maxPage) {
                        searchAdapter.loadMoreEnd()
                        searchAdapter.setEnableLoadMore(false)
                    } else {
                        searchAdapter.loadMoreComplete()
                        searchAdapter.setEnableLoadMore(true)
                    }
                } else {
                    searchAdapter.loadMoreComplete()
                    searchAdapter.setNewData(data)
                    searchAdapter.setEnableLoadMore(false)
                }
            } else {
                if (page > maxPage) {
                    searchAdapter.loadMoreEnd()
                } else {
                    searchAdapter.loadMoreComplete()
                }
                searchAdapter.setEnableLoadMore(false)
            }
        })
    }

    private fun loadSubtitle(subtitle: Subtitle) {
        val loader = mSubtitleLoader ?: return
        subtitleViewModel?.getSubtitleUrl(subtitle, loader)
    }

    fun setSubtitleLoader(subtitleLoader: SubtitleLoader?) {
        mSubtitleLoader = subtitleLoader
    }

    interface SubtitleLoader {
        fun loadSubtitle(subtitle: Subtitle)
    }

    override fun onBackPressed() {
        if (!isSearchPag) {
            isSearchPag = true
            loadingBar.visibility = View.GONE
            mGridView.visibility = View.VISIBLE
            searchAdapter.setNewData(zipSubtitles)
            searchAdapter.setEnableLoadMore(page < maxPage)
            return
        }
        dismiss()
    }
}
