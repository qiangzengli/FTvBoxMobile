package xyz.doikki.videoplayer.exo

import android.content.Context
import xyz.doikki.videoplayer.player.PlayerFactory

class ExoMediaPlayerFactory : PlayerFactory<ExoMediaPlayer>() {

    override fun createPlayer(context: Context): ExoMediaPlayer {
        return ExoMediaPlayer(context)
    }

    companion object {
        @JvmStatic
        fun create(): ExoMediaPlayerFactory {
            return ExoMediaPlayerFactory()
        }
    }
}
