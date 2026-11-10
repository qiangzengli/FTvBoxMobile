package com.github.tvbox.osc.ui.dialog

import android.content.Context
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.QuickSearchAdapter
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList

class QuickSearchDialog(context: Context) : BaseDialog(context, R.style.CustomDialogStyleDim) {
    private lateinit var searchWordAdapter: SearchWordAdapter
    private lateinit var searchAdapter: QuickSearchAdapter
    private lateinit var mGridView: TvRecyclerView
    private lateinit var mGridViewWord: TvRecyclerView

    init {
        setCanceledOnTouchOutside(false)
        setCancelable(true)
        setContentView(R.layout.dialog_quick_search)
        init(context)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_QUICK_SEARCH) {
            val data = event.obj as? List<Movie.Video>
            if (data != null) {
                searchAdapter.addData(data)
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD) {
            val data = event.obj as? List<String>
            if (data != null) {
                searchWordAdapter.setNewData(data)
            }
        }
    }

    private fun init(context: Context) {
        EventBus.getDefault().register(this)
        setOnDismissListener {
            EventBus.getDefault().unregister(this)
        }
        mGridView = findViewById(R.id.mGridView)
        searchAdapter = QuickSearchAdapter()
        mGridView.setHasFixedSize(true)
        mGridView.layoutManager = V7LinearLayoutManager(context, 1, false)
        mGridView.adapter = searchAdapter
        searchAdapter.setOnItemClickListener { _, _, position ->
            val video = searchAdapter.data[position]
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_SELECT, video))
            dismiss()
        }
        searchAdapter.setNewData(ArrayList())

        searchWordAdapter = SearchWordAdapter()
        mGridViewWord = findViewById(R.id.mGridViewWord)
        mGridViewWord.adapter = searchWordAdapter
        mGridViewWord.layoutManager = V7LinearLayoutManager(context, 0, false)
        searchWordAdapter.setOnItemClickListener { _, _, position ->
            searchAdapter.data.clear()
            searchAdapter.notifyDataSetChanged()
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE, searchWordAdapter.data[position]))
        }
        searchWordAdapter.setNewData(ArrayList())
    }
}
