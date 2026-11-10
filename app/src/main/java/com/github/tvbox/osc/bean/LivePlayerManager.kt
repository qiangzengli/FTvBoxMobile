package com.github.tvbox.osc.bean

import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.PlayerHelper
import com.orhanobut.hawk.Hawk
import org.json.JSONException
import org.json.JSONObject
import xyz.doikki.videoplayer.player.VideoView

class LivePlayerManager {
    private var defaultPlayerConfig = JSONObject()
    private var currentPlayerConfig: JSONObject? = null

    fun init(videoView: VideoView<*>) {
        try {
            defaultPlayerConfig.put("pl", Hawk.get(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 2)))
            if (defaultPlayerConfig.optInt("pl", 2) == 0) {
                defaultPlayerConfig.put("pl", 2)
            }
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"))
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0))
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        getDefaultLiveChannelPlayer(videoView)
    }

    fun getDefaultLiveChannelPlayer(videoView: VideoView<*>) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig)
        try {
            currentPlayerConfig = JSONObject(defaultPlayerConfig.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun getLivePlayerType(): Int {
        var playerTypeIndex = 2
        try {
            val playerType = currentPlayerConfig?.getInt("pl") ?: 2
            val ijkCodec = currentPlayerConfig?.getString("ijk") ?: "软解码"
            when (playerType) {
                1 -> {
                    playerTypeIndex = if (ijkCodec == "硬解码") 0 else 1
                }
                2 -> playerTypeIndex = 2
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return playerTypeIndex
    }

    fun getLivePlayerScale(): Int {
        return try {
            currentPlayerConfig?.getInt("sc") ?: 0
        } catch (e: JSONException) {
            e.printStackTrace()
            0
        }
    }

    fun changeLivePlayerType(videoView: VideoView<*>, playerType: Int) {
        val playerConfig = currentPlayerConfig ?: JSONObject()
        try {
            when (playerType) {
                0 -> {
                    playerConfig.put("pl", 1)
                    playerConfig.put("ijk", "硬解码")
                }
                1 -> {
                    playerConfig.put("pl", 1)
                    playerConfig.put("ijk", "软解码")
                }
                2 -> {
                    playerConfig.put("pl", 2)
                    playerConfig.put("ijk", "软解码")
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        PlayerHelper.updateCfg(videoView, playerConfig)

        try {
            defaultPlayerConfig.put("pl", playerConfig.getInt("pl"))
            defaultPlayerConfig.put("ijk", playerConfig.getString("ijk"))
            Hawk.put(HawkConfig.LIVE_PLAY_TYPE, playerConfig.getInt("pl"))
            Hawk.put(HawkConfig.IJK_CODEC, playerConfig.getString("ijk"))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        currentPlayerConfig = playerConfig
    }

    fun switchLivePlayer(videoView: VideoView<*>): Boolean {
        val playerConfig = currentPlayerConfig
        if (playerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config")
            return false
        }
        try {
            val playerType = playerConfig.getInt("pl")
            val switchPlayerType = if (playerType == 1) 2 else if (playerType == 2) 1 else playerType
            if (switchPlayerType == playerType) {
                LOG.i("echo-liveSwitchPlayer: skip unsupported playerType=$playerType")
                return false
            }
            LOG.i("echo-liveSwitchPlayer: $playerType -> $switchPlayerType")
            playerConfig.put("pl", switchPlayerType)
        } catch (e: JSONException) {
            LOG.i("echo-liveSwitchPlayer error: " + e.message)
            return false
        }
        PlayerHelper.updateCfg(videoView, playerConfig)

        currentPlayerConfig = playerConfig
        return true
    }

    fun changeLivePlayerScale(videoView: VideoView<*>, playerScale: Int) {
        videoView.setScreenScaleType(playerScale)
        Hawk.put(HawkConfig.LIVE_PLAY_SCALE, playerScale)

        val playerConfig = currentPlayerConfig ?: JSONObject()
        try {
            playerConfig.put("sc", playerScale)
            defaultPlayerConfig.put("sc", playerScale)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        currentPlayerConfig = playerConfig
    }
}
