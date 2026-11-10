package com.github.tvbox.osc.ui.activity

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.receiver.PushReceiver

/** Phone entry point for direct links and links handled by the subscription's push source. */
class PushActivity : BaseActivity() {
    override fun getLayoutResID(): Int = R.layout.activity_push

    override fun init() {
        val input: EditText = findViewById(R.id.mobileLinkInput)
        findViewById<View>(R.id.pushLocal).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.primaryClip == null) {
                Toast.makeText(this, "剪贴板中没有内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)
            input.setText(value?.toString()?.trim() ?: "")
            input.setSelection(input.length())
        }
        findViewById<View>(R.id.mobilePlayLink).setOnClickListener { play(input) }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                play(input)
                true
            } else {
                false
            }
        }
    }

    private fun play(input: EditText) {
        val url = input.text.toString().trim()
        if (url.isEmpty()) {
            input.error = "请输入播放链接"
            return
        }
        PushReceiver.send(this, url)
    }
}
