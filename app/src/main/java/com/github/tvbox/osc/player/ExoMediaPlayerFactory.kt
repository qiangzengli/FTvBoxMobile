package com.github.tvbox.osc.player

import android.content.Context
import xyz.doikki.videoplayer.player.PlayerFactory

class ExoMediaPlayerFactory : PlayerFactory<ExoPlayer>() {

    companion object {
        @JvmStatic
        fun create(): ExoMediaPlayerFactory {
            return ExoMediaPlayerFactory()
        }
    }

    override fun createPlayer(context: Context): ExoPlayer {
        return ExoPlayer(context)
    }
}
