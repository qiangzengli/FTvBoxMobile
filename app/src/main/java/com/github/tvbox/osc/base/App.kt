package com.github.tvbox.osc.base

import android.app.Activity
import androidx.multidex.MultiDexApplication
import com.fbox.mobile.LegacyFlutterImport
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.github.tvbox.osc.data.AppDataManager
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.AppManager
import com.github.tvbox.osc.util.EpgUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.kingja.loadsir.core.LoadSir
import com.orhanobut.hawk.Hawk
import com.p2p.P2PClass
import com.whl.quickjs.android.QuickJSLoader
import me.jessyan.autosize.AutoSizeConfig
import me.jessyan.autosize.unit.Subunits

class App : MultiDexApplication() {

    @JvmField
    var vodInfo: VodInfo? = null

    @JvmField
    var dashData: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        initParams()
        // OKGo
        OkGoHelper.init()
        EpgUtil.init()
        // 初始化Web服务器
        ControlManager.init(this)
        // 初始化数据库
        AppDataManager.init()
        LegacyFlutterImport.run(this)
        LoadSir.beginBuilder()
            .addCallback(EmptyCallback())
            .addCallback(LoadingCallback())
            .commit()
        AutoSizeConfig.getInstance().setCustomFragment(true).unitsManager
            .setSupportDP(false)
            .setSupportSP(false)
            .supportSubunits = Subunits.MM
        PlayerHelper.init()
        QuickJSLoader.init()
        FileUtils.cleanPlayerCache()
    }

    private fun initParams() {
        Hawk.init(this).build()
        Hawk.put(HawkConfig.DEBUG_OPEN, false)
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, false)
        if (!Hawk.contains(HawkConfig.HOME_REC_STYLE)) Hawk.put(HawkConfig.HOME_REC_STYLE, true)
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 2)
        } else if (Hawk.get(HawkConfig.PLAY_TYPE, 2) == 0) {
            Hawk.put(HawkConfig.PLAY_TYPE, 2)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        JsLoader.destroy()
    }

    fun setVodInfo(vodinfo: VodInfo?) {
        this.vodInfo = vodinfo
    }

    fun getVodInfo(): VodInfo? {
        return this.vodInfo
    }

    val currentActivity: Activity?
        get() = AppManager.getInstance().currentActivity()

    fun setDashData(data: String?) {
        dashData = data
    }

    fun getDashData(): String? {
        return dashData
    }

    companion object {
        private var instance: App? = null

        @JvmStatic
        fun getInstance(): App {
            return instance!!
        }

        private var p: P2PClass? = null

        @JvmField
        var burl: String? = null

        private var dashData: String? = null

        @JvmStatic
        fun getp2p(): P2PClass? {
            return try {
                if (p == null) {
                    p = P2PClass(FileUtils.getExternalCachePath())
                }
                p
            } catch (e: Exception) {
                LOG.e(e.toString())
                null
            }
        }
    }
}
