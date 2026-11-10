package com.github.tvbox.osc.ui.dialog

import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.github.tvbox.osc.R
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
class ApiDialog(context: Context) : BaseDialog(context) {
    private val inputApi: EditText
    private val inputApiLive: EditText
    private val inputConfirm: View
    private var listener: OnListener? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            inputApi.setText((event.obj as? String) ?: "")
            inputApiLive.setText((event.obj as? String) ?: "")
        } else if (event.type == RefreshEvent.TYPE_LIVE_API_URL_CHANGE) {
            inputApiLive.setText((event.obj as? String) ?: "")
        }
    }

    init {
        setContentView(R.layout.dialog_api)
        setCanceledOnTouchOutside(false)
        inputApi = findViewById(R.id.input)
        inputApiLive = findViewById(R.id.inputLive)
        inputConfirm = findViewById(R.id.inputConfirm)

        inputApi.setText(Hawk.get(HawkConfig.API_URL, ""))
        inputApiLive.setText(Hawk.get(HawkConfig.LIVE_API_URL, Hawk.get(HawkConfig.API_URL, "")))

        findViewById<View>(R.id.inputSubmit).setOnClickListener {
            listener?.onLocalConfig(false)
        }
        findViewById<View>(R.id.inputSubmitLive).setOnClickListener {
            listener?.onLocalConfig(true)
        }
        inputConfirm.setOnClickListener {
            saveAndDismiss()
        }
        findViewById<View>(R.id.apiHistory).setOnClickListener {
            val history = Hawk.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
            if (history.isEmpty()) {
                Toast.makeText(getContext(), "直播历史为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val current = Hawk.get(HawkConfig.LIVE_API_URL, "")
            var idx = 0
            if (history.contains(current)) {
                idx = history.indexOf(current)
            }
            val dialog = ApiHistoryDialog(getContext())
            dialog.setTip("直播历史配置")
            dialog.setAdapter(object : ApiHistoryDialogAdapter.SelectDialogInterface {
                override fun click(value: String) {
                    inputApiLive.setText(value)
                    dialog.dismiss()
                }

                override fun del(value: String, data: ArrayList<String>) {
                    Hawk.put(HawkConfig.LIVE_API_HISTORY, data)
                }
            }, history, idx)
            dialog.show()
        }
        findViewById<View>(R.id.mobilePasteApi).setOnClickListener { pasteInto(inputApi) }
        findViewById<View>(R.id.mobilePasteLive).setOnClickListener { pasteInto(inputApiLive) }
        findViewById<View>(R.id.mobileCancelApi).setOnClickListener { cancel() }

        inputApi.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || isSoftKeyboardEnter(actionId, event)) {
                inputApiLive.requestFocus()
                inputApiLive.setSelection(inputApiLive.text?.length ?: 0)
                true
            } else {
                false
            }
        }
        inputApiLive.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || isSoftKeyboardEnter(actionId, event)) {
                focusConfirm()
                true
            } else {
                false
            }
        }
    }

    private fun isSoftKeyboardEnter(actionId: Int, event: KeyEvent?): Boolean {
        return actionId == EditorInfo.IME_NULL
                && event != null
                && event.action == KeyEvent.ACTION_UP
                && event.keyCode == KeyEvent.KEYCODE_ENTER
    }

    private fun focusConfirm() {
        val imm = inputApiLive.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(inputApiLive.windowToken, 0)
        inputApiLive.clearFocus()
        inputConfirm.requestFocus()
    }

    private fun pasteInto(input: EditText) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.primaryClip == null) {
            Toast.makeText(context, "剪贴板中没有内容", Toast.LENGTH_SHORT).show()
            return
        }
        val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)
        input.setText(value?.toString()?.trim() ?: "")
        input.setSelection(input.length())
    }

    private fun saveAndDismiss() {
        val newApi = inputApi.text.toString().trim()
        var newLiveApi = inputApiLive.text.toString().trim()
        if (newApi.isNotEmpty()) {
            HistoryHelper.setApiHistory(newApi)
        }
        if (newLiveApi.isEmpty()) newLiveApi = newApi
        if (newLiveApi.isNotEmpty()) {
            HistoryHelper.setLiveApiHistory(newLiveApi)
        }
        Hawk.put(HawkConfig.LIVE_API_URL, newLiveApi)
        listener?.onchange(newApi)
        dismiss()
    }

    fun setOnListener(listener: OnListener?) {
        this.listener = listener
    }

    fun setLocalApi(api: String?, live: Boolean) {
        if (live) {
            inputApiLive.setText(api)
        } else {
            inputApi.setText(api)
        }
    }

    interface OnListener {
        fun onchange(api: String)
        fun onLocalConfig(live: Boolean)
    }
}
