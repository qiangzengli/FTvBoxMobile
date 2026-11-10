package com.github.tvbox.osc.ui.tv.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.github.tvbox.osc.R
import java.util.ArrayList
import java.util.Arrays

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
class SearchKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var mRecyclerView: RecyclerView? = null
    private val keys: List<String> = Arrays.asList(
        "远程搜索", "删除", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
        "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    )
    private val keyboardList: MutableList<Keyboard> = ArrayList()
    private var searchKeyListener: OnSearchKeyListener? = null
    private val focusChangeListener = OnFocusChangeListener { itemView, hasFocus ->
        if (itemView != null && itemView !== mRecyclerView) {
            itemView.isSelected = hasFocus
        }
    }

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_keyborad, this)
        val rv = view.findViewById<RecyclerView>(R.id.mRecyclerView)
        mRecyclerView = rv
        val manager = GridLayoutManager(context, 6)
        rv.layoutManager = manager
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(child: View) {
                if (child.isFocusable && child.onFocusChangeListener == null) {
                    child.onFocusChangeListener = focusChangeListener
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
            }
        })
        val size = keys.size
        for (i in 0 until size) {
            keyboardList.add(Keyboard(1, keys[i]))
        }
        val adapter = KeyboardAdapter(keyboardList)
        rv.adapter = adapter
        adapter.setSpanSizeLookup { _, position ->
            if (position == 0) 3 else if (position == 1) 3 else 1
        }

        adapter.setOnItemClickListener { ad, _, position ->
            val keyboard = ad.getItem(position) as? Keyboard
            if (searchKeyListener != null && keyboard != null) {
                searchKeyListener?.onSearchKey(position, keyboard.key)
            }
        }
    }

    class Keyboard(
        private val itemType: Int,
        var key: String
    ) : MultiItemEntity {
        override fun getItemType(): Int {
            return itemType
        }
    }

    private class KeyboardAdapter(data: List<Keyboard>?) :
        BaseMultiItemQuickAdapter<Keyboard, BaseViewHolder>(data) {

        init {
            addItemType(1, R.layout.item_keyboard)
        }

        override fun convert(helper: BaseViewHolder, item: Keyboard) {
            when (helper.itemViewType) {
                1 -> helper.setText(R.id.keyName, item.key)
            }
        }
    }

    fun setOnSearchKeyListener(listener: OnSearchKeyListener?) {
        searchKeyListener = listener
    }

    fun interface OnSearchKeyListener {
        fun onSearchKey(pos: Int, key: String)
    }
}
