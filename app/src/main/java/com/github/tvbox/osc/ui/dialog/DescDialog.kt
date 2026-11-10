package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.widget.TextView
import com.github.tvbox.osc.R

class DescDialog(context: Context) : BaseDialog(context) {

    init {
        setContentView(R.layout.dialog_desc)
    }

    fun setDescribe(describe: String?) {
        val tvDescribe = findViewById<TextView>(R.id.describe)
        tvDescribe.text = describe
        tvDescribe.requestFocus()
        tvDescribe.requestFocusFromTouch()
    }
}
