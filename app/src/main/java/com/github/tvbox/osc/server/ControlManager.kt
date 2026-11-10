package com.github.tvbox.osc.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.receiver.PushReceiver
import com.github.tvbox.osc.receiver.SearchReceiver
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
class ControlManager private constructor() {
    private var mServer: RemoteServer? = null

    fun getAddress(local: Boolean): String {
        if (mServer == null || !mServer!!.isStarting()) {
            startServer()
        }
        if (mServer == null || !mServer!!.isStarting()) {
            return ""
        }
        return if (local) mServer!!.loadAddress else mServer!!.serverAddress
    }

    fun startServer() {
        if (mServer != null && mServer!!.isStarting()) {
            return
        }
        val context = mContext ?: return
        do {
            mServer = RemoteServer(RemoteServer.serverPort, context)
            mServer!!.dataReceiver = object : DataReceiver {
                override fun onTextReceived(text: String) {
                    if (!TextUtils.isEmpty(text)) {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putString("title", text)
                        intent.action = SearchReceiver.action
                        intent.setPackage(context.packageName)
                        intent.component = ComponentName(context, SearchReceiver::class.java)
                        intent.putExtras(bundle)
                        context.sendBroadcast(intent)
                    }
                }

                override fun onApiReceived(url: String) {
                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url))
                }

                override fun onLiveApiReceived(url: String) {
                    if (!TextUtils.isEmpty(url)) {
                        Hawk.put(HawkConfig.LIVE_API_URL, url)
                        HistoryHelper.setLiveApiHistory(url)
                    }
                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_LIVE_API_URL_CHANGE, url))
                }

                override fun onDanmuApiReceived(url: String) {
                    Hawk.put(HawkConfig.DANMU_API, if (TextUtils.isEmpty(url)) "" else url)
                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
                }

                override fun onPushReceived(url: String) {
                    PushReceiver.send(context, url)
                }
            }
            try {
                mServer!!.start()
                com.github.catvod.Proxy.set(RemoteServer.serverPort)
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort)
                break
            } catch (ex: IOException) {
                RemoteServer.serverPort++
                mServer!!.stop()
            }
        } while (RemoteServer.serverPort < 9999)
    }

    fun stopServer() {
        if (mServer != null && mServer!!.isStarting()) {
            mServer!!.stop()
        }
        mServer = null
    }

    companion object {
        private var instance: ControlManager? = null

        @JvmField
        var mContext: Context? = null

        @JvmStatic
        fun get(): ControlManager {
            if (instance == null) {
                synchronized(ControlManager::class.java) {
                    if (instance == null) {
                        instance = ControlManager()
                    }
                }
            }
            return instance!!
        }

        @JvmStatic
        fun init(context: Context) {
            mContext = context
        }
    }
}
