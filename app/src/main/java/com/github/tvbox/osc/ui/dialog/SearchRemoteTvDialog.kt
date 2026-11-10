package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.R
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment
import com.kingja.loadsir.core.LoadService
import com.kingja.loadsir.core.LoadSir
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SearchRemoteTvDialog(context: Context) : BaseDialog(context) {

    private var mLoadService: LoadService<Any>? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SETTING_SEARCH_TV) {
            showRemoteTvDialog(ModelSettingFragment.foundRemoteTv)
        }
    }

    init {
        setContentView(R.layout.dialog_search_remotetv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setTip(tip: String?) {
        findViewById<TextView>(R.id.title)?.text = tip
        setLoadSir(findViewById(R.id.list))
        showLoading()
    }

    private fun showRemoteTvDialog(found: Boolean) {
        if (!found) {
            ModelSettingFragment.loadingSearchRemoteTvDialog?.showEmpty()
            Toast.makeText(context, "未找到附近TVBox", Toast.LENGTH_SHORT).show()
            return
        }
        ModelSettingFragment.loadingSearchRemoteTvDialog?.dismiss()
        val hosts = ModelSettingFragment.remoteTvHostList ?: return
        if (hosts.isNotEmpty()) {
            RemoteTVBox.setAvalible(hosts[0])
        }
        val dialog = SelectDialog<String>(context)
        dialog.setTip("附近TVBox")
        val defaultPos = 0
        dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<String> {
            override fun click(value: String, pos: Int) {
                RemoteTVBox.setAvalible(value)
                Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
            }

            override fun getDisplay(valStr: String): String {
                return valStr
            }
        }, object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }, hosts, defaultPos)
        dialog.show()
    }

    protected fun setLoadSir(view: View) {
        if (mLoadService == null) {
            @Suppress("UNCHECKED_CAST")
            mLoadService = LoadSir.getDefault().register(view) {} as? LoadService<Any>
        }
    }

    fun showLoading() {
        mLoadService?.showCallback(LoadingCallback::class.java)
    }

    fun showEmpty() {
        mLoadService?.showCallback(EmptyCallback::class.java)
    }

    fun showSuccess() {
        mLoadService?.showSuccess()
    }
}
