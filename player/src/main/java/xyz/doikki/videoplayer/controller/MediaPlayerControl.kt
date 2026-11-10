package xyz.doikki.videoplayer.controller

import android.graphics.Bitmap

interface MediaPlayerControl {

    fun start()

    fun pause()

    fun getDuration(): Long

    fun getCurrentPosition(): Long

    fun seekTo(pos: Long)

    fun isPlaying(): Boolean

    fun getBufferedPercentage(): Int

    fun startFullScreen()

    fun stopFullScreen()

    fun isFullScreen(): Boolean

    fun setMute(isMute: Boolean)

    fun isMute(): Boolean

    fun setScreenScaleType(screenScaleType: Int)

    fun setSpeed(speed: Float)

    fun getSpeed(): Float

    fun getTcpSpeed(): Long

    fun replay(resetPosition: Boolean)

    fun setMirrorRotation(enable: Boolean)

    fun doScreenShot(): Bitmap?

    fun getVideoSize(): IntArray

    fun setRotation(rotation: Float)

    fun startTinyScreen()

    fun stopTinyScreen()

    fun isTinyScreen(): Boolean
}
