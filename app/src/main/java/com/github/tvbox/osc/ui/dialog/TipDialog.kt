package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.widget.TextView
import com.github.tvbox.osc.R

class TipDialog(
    context: Context,
    tip: String?,
    left: String?,
    right: String?,
    listener: OnListener
) : BaseDialog(context) {

    init {
        setContentView(R.layout.dialog_tip)
        setCanceledOnTouchOutside(false)
        val tipInfo = findViewById<TextView>(R.id.tipInfo)
        val leftBtn = findViewById<TextView>(R.id.leftBtn)
        val rightBtn = findViewById<TextView>(R.id.rightBtn)
        tipInfo.text = tip
        leftBtn.text = left
        rightBtn.text = right
        leftBtn.setOnClickListener {
            listener.left()
        }
        rightBtn.setOnClickListener {
            listener.right()
        }
        setOnCancelListener {
            listener.cancel()
        }
    }

    interface OnListener {
        fun left()
        fun right()
        fun cancel()
    }
}
