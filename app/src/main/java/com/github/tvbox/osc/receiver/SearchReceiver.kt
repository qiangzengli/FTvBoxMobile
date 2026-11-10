package com.github.tvbox.osc.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.github.tvbox.osc.util.AppManager
import org.greenrobot.eventbus.EventBus

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
class SearchReceiver : BroadcastReceiver() {
    companion object {
        @JvmField
        var action: String = "android.content.movie.search.Action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (action == intent.action && intent.extras != null) {
            val title = intent.extras?.getString("title")
            if (AppManager.getInstance().getActivity(SearchActivity::class.java) != null) {
                AppManager.getInstance().backActivity(SearchActivity::class.java)
                EventBus.getDefault().post(ServerEvent(ServerEvent.SERVER_SEARCH, title))
            } else {
                val newIntent = Intent(context, SearchActivity::class.java)
                newIntent.putExtra("title", title)
                newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(newIntent)
            }
        }
    }
}
