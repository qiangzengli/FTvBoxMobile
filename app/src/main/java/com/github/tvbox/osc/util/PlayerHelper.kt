package com.github.tvbox.osc.util

import android.app.Activity
import android.content.Context
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.player.ExoMediaPlayerFactory
import com.github.tvbox.osc.player.IjkMediaPlayer
import com.github.tvbox.osc.player.render.SurfaceRenderViewFactory
import com.github.tvbox.osc.player.thirdparty.Kodi
import com.github.tvbox.osc.player.thirdparty.MXPlayer
import com.github.tvbox.osc.player.thirdparty.ReexPlayer
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox
import com.github.tvbox.osc.player.thirdparty.VlcPlayer
import com.orhanobut.hawk.Hawk
import org.json.JSONException
import org.json.JSONObject
import tv.danmaku.ijk.media.player.IjkLibLoader
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.PlayerFactory
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.render.RenderViewFactory
import xyz.doikki.videoplayer.render.TextureRenderViewFactory
import java.text.DecimalFormat
import java.util.ArrayList
import java.util.HashMap

object PlayerHelper {
    @JvmStatic
    fun updateCfg(videoView: VideoView<*>?, playerCfg: JSONObject?) {
        updateCfg(videoView, playerCfg, -1)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun updateCfg(videoView: VideoView<*>?, playerCfg: JSONObject?, forcePlayerType: Int) {
        var playerType = Hawk.get(HawkConfig.PLAY_TYPE, 2)
        var renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0)
        var ijkCode = Hawk.get(HawkConfig.IJK_CODEC, "硬解码")
        var scale = Hawk.get(HawkConfig.PLAY_SCALE, 0)
        if (playerCfg != null) {
            try {
                playerType = playerCfg.getInt("pl")
                renderType = playerCfg.getInt("pr")
                ijkCode = playerCfg.getString("ijk")
                scale = playerCfg.getInt("sc")
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        if (forcePlayerType >= 0) playerType = forcePlayerType
        val codec = ApiConfig.get().getIJKCodec(ijkCode)
        val playerFactory: PlayerFactory<*>
        if (playerType == 1) {
            playerFactory = object : PlayerFactory<IjkMediaPlayer>() {
                override fun createPlayer(context: Context): IjkMediaPlayer {
                    return IjkMediaPlayer(context, codec)
                }
            }
            try {
                tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(object : IjkLibLoader {
                    override fun loadLibrary(s: String?) {
                        try {
                            s?.let { System.loadLibrary(it) }
                        } catch (th: Throwable) {
                            th.printStackTrace()
                        }
                    }
                })
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        } else {
            playerFactory = ExoMediaPlayerFactory.create()
        }
        val renderViewFactory: RenderViewFactory = when (renderType) {
            1 -> SurfaceRenderViewFactory.create()
            0 -> TextureRenderViewFactory.create()
            else -> TextureRenderViewFactory.create()
        }
        if (videoView != null) {
            (videoView as VideoView<AbstractPlayer>).setPlayerFactory(playerFactory as PlayerFactory<AbstractPlayer>)
            videoView.setRenderViewFactory(renderViewFactory)
            videoView.setScreenScaleType(scale)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun updateCfg(videoView: VideoView<*>?) {
        val playType = Hawk.get(HawkConfig.PLAY_TYPE, 2)
        val playerFactory: PlayerFactory<*>
        if (playType == 1) {
            playerFactory = object : PlayerFactory<IjkMediaPlayer>() {
                override fun createPlayer(context: Context): IjkMediaPlayer {
                    return IjkMediaPlayer(context, null)
                }
            }
            try {
                tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(object : IjkLibLoader {
                    override fun loadLibrary(s: String?) {
                        try {
                            s?.let { System.loadLibrary(it) }
                        } catch (th: Throwable) {
                            th.printStackTrace()
                        }
                    }
                })
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        } else {
            playerFactory = ExoMediaPlayerFactory.create()
        }
        val renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0)
        val renderViewFactory: RenderViewFactory = when (renderType) {
            1 -> SurfaceRenderViewFactory.create()
            0 -> TextureRenderViewFactory.create()
            else -> TextureRenderViewFactory.create()
        }
        if (videoView != null) {
            (videoView as VideoView<AbstractPlayer>).setPlayerFactory(playerFactory as PlayerFactory<AbstractPlayer>)
            videoView.setRenderViewFactory(renderViewFactory)
        }
    }

    @JvmStatic
    fun init() {
        try {
            tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(object : IjkLibLoader {
                override fun loadLibrary(s: String?) {
                    try {
                        s?.let { System.loadLibrary(it) }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            })
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    @JvmStatic
    fun getPlayerName(playType: Int): String {
        val playersInfo = getPlayersInfo()
        return playersInfo[playType] ?: "EXO播放器"
    }

    private var mPlayersInfo: HashMap<Int, String>? = null

    @JvmStatic
    fun getPlayersInfo(): HashMap<Int, String> {
        if (mPlayersInfo == null) {
            val playersInfo = HashMap<Int, String>()
            playersInfo[1] = "IJK播放器"
            playersInfo[2] = "EXO播放器"
            playersInfo[10] = "MX播放器"
            playersInfo[11] = "Reex播放器"
            playersInfo[12] = "Kodi播放器"
            playersInfo[13] = "附近TVBox"
            playersInfo[14] = "VLC播放器"
            mPlayersInfo = playersInfo
        }
        return mPlayersInfo!!
    }

    private fun hasPlayerPackage(clazz: Class<*>): Boolean {
        return try {
            clazz.getMethod("getPackageInfo").invoke(null) != null
        } catch (e: Exception) {
            false
        }
    }

    private var mPlayersExistInfo: HashMap<Int, Boolean>? = null

    @JvmStatic
    fun getPlayersExistInfo(): HashMap<Int, Boolean> {
        if (mPlayersExistInfo == null) {
            val playersExist = HashMap<Int, Boolean>()
            playersExist[1] = true
            playersExist[2] = true
            playersExist[10] = hasPlayerPackage(MXPlayer::class.java)
            playersExist[11] = hasPlayerPackage(ReexPlayer::class.java)
            playersExist[12] = hasPlayerPackage(Kodi::class.java)
            playersExist[13] = RemoteTVBox.getAvalible() != null
            playersExist[14] = hasPlayerPackage(VlcPlayer::class.java)
            mPlayersExistInfo = playersExist
        }
        return mPlayersExistInfo!!
    }

    @JvmStatic
    fun getPlayerExist(playType: Int): Boolean {
        val playersExistInfo = getPlayersExistInfo()
        return playersExistInfo[playType] ?: false
    }

    @JvmStatic
    fun getExistPlayerTypes(): ArrayList<Int> {
        val playersExistInfo = getPlayersExistInfo()
        val existPlayers = ArrayList<Int>()
        for (playerType in playersExistInfo.keys) {
            if (playersExistInfo[playerType] == true) {
                existPlayers.add(playerType)
            }
        }
        return existPlayers
    }

    @JvmStatic
    fun runExternalPlayer(
        playerType: Int,
        activity: Activity?,
        url: String?,
        title: String?,
        subtitle: String?,
        headers: HashMap<String, String>?
    ): Boolean {
        return runExternalPlayer(playerType, activity, url, title, subtitle, headers, 0L)
    }

    @JvmStatic
    fun runExternalPlayer(
        playerType: Int,
        activity: Activity?,
        url: String?,
        title: String?,
        subtitle: String?,
        headers: HashMap<String, String>?,
        progress: Long
    ): Boolean {
        var callResult = false
        when (playerType) {
            10 -> callResult = MXPlayer.run(activity, url, title, subtitle, headers)
            11 -> callResult = ReexPlayer.run(activity, url, title, subtitle, headers)
            12 -> callResult = Kodi.run(activity, url, title, subtitle, headers)
            13 -> callResult = RemoteTVBox.run(activity, url, title, subtitle, headers)
            14 -> callResult = VlcPlayer.run(activity, url, title, subtitle, progress)
        }
        return callResult
    }

    @JvmStatic
    fun getRenderName(renderType: Int): String {
        return if (renderType == 1) {
            "SurfaceView"
        } else {
            "TextureView"
        }
    }

    @JvmStatic
    fun getScaleName(screenScaleType: Int): String {
        return when (screenScaleType) {
            VideoView.SCREEN_SCALE_DEFAULT -> "默认"
            VideoView.SCREEN_SCALE_16_9 -> "16:9"
            VideoView.SCREEN_SCALE_4_3 -> "4:3"
            VideoView.SCREEN_SCALE_MATCH_PARENT -> "填充"
            VideoView.SCREEN_SCALE_ORIGINAL -> "原始"
            VideoView.SCREEN_SCALE_CENTER_CROP -> "裁剪"
            else -> "默认"
        }
    }

    @JvmStatic
    fun getDisplaySpeed(speed: Long, show: Boolean): String {
        return if (speed > 1048576) {
            DecimalFormat("#.00").format(speed / 1048576.0) + "Mb/s"
        } else if (speed > 1024) {
            (speed / 1024).toString() + "Kb/s"
        } else {
            if (speed > 0) speed.toString() + "B/s" else if (show) "0B/s" else ""
        }
    }

    @JvmStatic
    fun getDisplaySpeedBps(speed: Long, show: Boolean): String {
        val bitSpeed = speed * 8 // 字节转比特
        return if (bitSpeed >= 1_000_000_000) {
            DecimalFormat("0.00").format(bitSpeed / 1_000_000_000.0) + "Gbps"
        } else if (bitSpeed >= 1_000_000) {
            DecimalFormat("0.0").format(bitSpeed / 1_000_000.0) + "Mbps"
        } else if (bitSpeed >= 1_000) {
            DecimalFormat("0.0").format(bitSpeed / 1_000.0) + "Kbps"
        } else {
            if (bitSpeed > 0) bitSpeed.toString() + "bps" else if (show) "0bps" else ""
        }
    }
}
