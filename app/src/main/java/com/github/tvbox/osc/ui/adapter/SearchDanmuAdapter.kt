package com.github.tvbox.osc.ui.adapter

import android.view.KeyEvent
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.DanmuSearchResult
import java.util.ArrayList

class SearchDanmuAdapter : BaseQuickAdapter<DanmuSearchResult, BaseViewHolder>(
    R.layout.item_search_danmu_result,
    ArrayList()
) {
    override fun convert(helper: BaseViewHolder, item: DanmuSearchResult?) {
        if (item == null) return
        helper.setText(R.id.danmuName, item.getName())
        helper.addOnClickListener(R.id.danmuItem)
        helper.getView<android.view.View>(R.id.danmuItem).setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ENTER ||
                 keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                 keyCode == KeyEvent.KEYCODE_BUTTON_A)
            ) {
                view.performClick()
                return@setOnKeyListener true
            }
            false
        }
    }
}
