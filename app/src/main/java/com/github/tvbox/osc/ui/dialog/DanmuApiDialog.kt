package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.DanmakuApi
import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk

class DanmuApiDialog(context: Context) : BaseDialog(context) {
    private val input: EditText
    private var listener: OnListener? = null

    init {
        setContentView(R.layout.dialog_danmu_api)
        setCanceledOnTouchOutside(false)
        input = findViewById(R.id.input)
        input.setText(Hawk.get(HawkConfig.DANMU_API, ""))
        input.hint = getDefaultApi()
        findViewById<View>(R.id.inputDefault).setOnClickListener { saveDefault() }
        findViewById<View>(R.id.inputSubmit).setOnClickListener { save(input.text.toString().trim()) }
        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                save(input.text.toString().trim())
                true
            } else {
                false
            }
        }
    }

    private fun getDefaultApi(): String {
        val api = DanmakuApi.getDisplayApiUrl()
        return if (api.isEmpty()) "请输入弹幕搜索地址" else api
    }

    private fun save(api: String) {
        DanmakuApi.setCustomApi(api)
        listener?.onChange(api)
        dismiss()
    }

    private fun saveDefault() {
        DanmakuApi.setUseDefault(true)
        listener?.onChange("")
        dismiss()
    }

    fun setOnListener(listener: OnListener?) {
        this.listener = listener
    }

    fun interface OnListener {
        fun onChange(api: String)
    }
}
