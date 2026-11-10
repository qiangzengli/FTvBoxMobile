package com.github.tvbox.osc.ui.dialog

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.DanmakuApi
import com.github.tvbox.osc.bean.DanmuSearchResult
import com.github.tvbox.osc.ui.adapter.SearchDanmuAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import java.util.ArrayList

class SearchDanmuDialog(context: Context) : BaseDialog(context) {
    private lateinit var gridView: TvRecyclerView
    private lateinit var searchAdapter: SearchDanmuAdapter
    private lateinit var searchInput: EditText
    private lateinit var loadingBar: ProgressBar
    private var danmuLoader: DanmuLoader? = null
    private var episode: String = ""

    init {
        if (context is Activity) {
            setOwnerActivity(context)
        }
        setContentView(R.layout.dialog_search_danmu)
        initView()
    }

    private fun initView() {
        loadingBar = findViewById(R.id.loadingBar)
        gridView = findViewById(R.id.mGridView)
        searchInput = findViewById(R.id.input)
        val searchButton = findViewById<TextView>(R.id.inputSubmit)
        searchAdapter = SearchDanmuAdapter()
        gridView.setHasFixedSize(true)
        gridView.layoutManager = V7LinearLayoutManager(context, 1, false)
        gridView.adapter = searchAdapter
        searchAdapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            loadDanmu(searchAdapter.data[position])
        }
        searchAdapter.onItemChildClickListener = BaseQuickAdapter.OnItemChildClickListener { _, view, position ->
            if (view.id != R.id.danmuItem) return@OnItemChildClickListener
            FastClickCheckUtil.check(view)
            loadDanmu(searchAdapter.data[position])
        }
        searchButton.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            search(searchInput.text.toString().trim())
        }
        searchAdapter.setNewData(ArrayList())
    }

    fun setEpisode(episode: String?) {
        this.episode = episode ?: ""
    }

    fun setSearchWord(word: String?) {
        val searchWord = word?.trim() ?: ""
        searchInput.setText(searchWord)
        searchInput.setSelection(searchWord.length)
        searchInput.requestFocus()
        search(searchWord)
    }

    fun setDanmuLoader(danmuLoader: DanmuLoader?) {
        this.danmuLoader = danmuLoader
    }

    private fun search(word: String) {
        com.fbox.mobile.MobileSheets.hideKeyboard(searchInput)
        searchAdapter.setNewData(ArrayList())
        if (TextUtils.isEmpty(word)) {
            Toast.makeText(context, "输入内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading()
        DanmakuApi.searchList(word, episode, object : DanmakuApi.SearchListCallback {
            override fun onSuccess(results: List<DanmuSearchResult>) {
                showResults(results)
            }

            override fun onError(message: String) {
                showResults(ArrayList())
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadDanmu(result: DanmuSearchResult) {
        showLoading()
        DanmakuApi.loadSearchResult(result, object : DanmakuApi.SearchResultCallback {
            override fun onSuccess(danmu: String) {
                danmuLoader?.loadDanmu(danmu)
                dismiss()
            }

            override fun onError(message: String) {
                loadingBar.visibility = View.GONE
                gridView.visibility = View.VISIBLE
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showLoading() {
        loadingBar.visibility = View.VISIBLE
        gridView.visibility = View.GONE
    }

    private fun showResults(results: List<DanmuSearchResult>?) {
        val res = results ?: ArrayList()
        loadingBar.visibility = View.GONE
        gridView.visibility = View.VISIBLE
        searchAdapter.setNewData(res)
        if (res.isEmpty()) {
            Toast.makeText(context, "未查询到匹配弹幕", Toast.LENGTH_SHORT).show()
            return
        }
        gridView.requestFocus()
    }

    override fun onBackPressed() {
        DanmakuApi.cancel()
        dismiss()
    }

    interface DanmuLoader {
        fun loadDanmu(danmu: String)
    }
}
