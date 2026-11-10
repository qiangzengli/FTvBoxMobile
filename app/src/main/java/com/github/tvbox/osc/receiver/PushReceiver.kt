package com.github.tvbox.osc.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.ui.activity.DetailActivity

class PushReceiver : BroadcastReceiver() {
    companion object {
        @JvmField
        var action: String = "android.content.movie.push.Action"

        @JvmStatic
        fun send(context: Context?, url: String?) {
            if (context == null || TextUtils.isEmpty(url)) return
            val intent = Intent()
            intent.action = action
            intent.setPackage(context.packageName)
            intent.component = ComponentName(context, PushReceiver::class.java)
            intent.putExtra("url", url)
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (action != intent.action || intent.extras == null) return
        val url = intent.extras?.getString("url")
        if (TextUtils.isEmpty(url) || ApiConfig.get().getSource("push_agent") == null) return
        val newIntent = Intent(context, DetailActivity::class.java)
        newIntent.putExtra("id", url)
        newIntent.putExtra("sourceKey", "push_agent")
        newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        context.startActivity(newIntent)
    }
}
