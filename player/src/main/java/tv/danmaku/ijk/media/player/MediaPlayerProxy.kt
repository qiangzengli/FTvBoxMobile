package tv.danmaku.ijk.media.player

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import tv.danmaku.ijk.media.player.misc.IMediaDataSource
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import java.io.FileDescriptor
import java.io.IOException

open class MediaPlayerProxy(protected val mBackEndMediaPlayer: IMediaPlayer) : IMediaPlayer {

    open fun getInternalMediaPlayer(): IMediaPlayer = mBackEndMediaPlayer

    override fun setDisplay(sh: SurfaceHolder?) {
        mBackEndMediaPlayer.setDisplay(sh)
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    override fun setSurface(surface: Surface?) {
        mBackEndMediaPlayer.setSurface(surface)
    }

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(context: Context, uri: Uri) {
        mBackEndMediaPlayer.setDataSource(context, uri)
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(context: Context, uri: Uri, headers: Map<String, String>?) {
        mBackEndMediaPlayer.setDataSource(context, uri, headers)
    }

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun setDataSource(fd: FileDescriptor) {
        mBackEndMediaPlayer.setDataSource(fd)
    }

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(path: String) {
        mBackEndMediaPlayer.setDataSource(path)
    }

    override fun setDataSource(mediaDataSource: IMediaDataSource) {
        mBackEndMediaPlayer.setDataSource(mediaDataSource)
    }

    override fun getDataSource(): String? = mBackEndMediaPlayer.getDataSource()

    @Throws(IllegalStateException::class)
    override fun prepareAsync() {
        mBackEndMediaPlayer.prepareAsync()
    }

    @Throws(IllegalStateException::class)
    override fun start() {
        mBackEndMediaPlayer.start()
    }

    @Throws(IllegalStateException::class)
    override fun stop() {
        mBackEndMediaPlayer.stop()
    }

    @Throws(IllegalStateException::class)
    override fun pause() {
        mBackEndMediaPlayer.pause()
    }

    override fun setScreenOnWhilePlaying(screenOn: Boolean) {
        mBackEndMediaPlayer.setScreenOnWhilePlaying(screenOn)
    }

    override fun getVideoWidth(): Int = mBackEndMediaPlayer.getVideoWidth()

    override fun getVideoHeight(): Int = mBackEndMediaPlayer.getVideoHeight()

    override fun isPlaying(): Boolean = mBackEndMediaPlayer.isPlaying()

    @Throws(IllegalStateException::class)
    override fun seekTo(msec: Long) {
        mBackEndMediaPlayer.seekTo(msec)
    }

    override fun getCurrentPosition(): Long = mBackEndMediaPlayer.getCurrentPosition()

    override fun getDuration(): Long = mBackEndMediaPlayer.getDuration()

    override fun release() {
        mBackEndMediaPlayer.release()
    }

    override fun reset() {
        mBackEndMediaPlayer.reset()
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mBackEndMediaPlayer.setVolume(leftVolume, rightVolume)
    }

    override fun getAudioSessionId(): Int = mBackEndMediaPlayer.getAudioSessionId()

    override fun getMediaInfo(): MediaInfo? = mBackEndMediaPlayer.getMediaInfo()

    @Deprecated("Deprecated in Java")
    override fun setLogEnabled(enable: Boolean) {
    }

    @Deprecated("Deprecated in Java")
    override fun isPlayable(): Boolean = false

    override fun setOnPreparedListener(listener: IMediaPlayer.OnPreparedListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnPreparedListener { listener.onPrepared(this@MediaPlayerProxy) }
        } else {
            mBackEndMediaPlayer.setOnPreparedListener(null)
        }
    }

    override fun setOnCompletionListener(listener: IMediaPlayer.OnCompletionListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnCompletionListener { listener.onCompletion(this@MediaPlayerProxy) }
        } else {
            mBackEndMediaPlayer.setOnCompletionListener(null)
        }
    }

    override fun setOnBufferingUpdateListener(listener: IMediaPlayer.OnBufferingUpdateListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnBufferingUpdateListener { _, percent ->
                listener.onBufferingUpdate(this@MediaPlayerProxy, percent)
            }
        } else {
            mBackEndMediaPlayer.setOnBufferingUpdateListener(null)
        }
    }

    override fun setOnSeekCompleteListener(listener: IMediaPlayer.OnSeekCompleteListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnSeekCompleteListener { listener.onSeekComplete(this@MediaPlayerProxy) }
        } else {
            mBackEndMediaPlayer.setOnSeekCompleteListener(null)
        }
    }

    override fun setOnVideoSizeChangedListener(listener: IMediaPlayer.OnVideoSizeChangedListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnVideoSizeChangedListener { _, width, height, sar_num, sar_den ->
                listener.onVideoSizeChanged(this@MediaPlayerProxy, width, height, sar_num, sar_den)
            }
        } else {
            mBackEndMediaPlayer.setOnVideoSizeChangedListener(null)
        }
    }

    override fun setOnErrorListener(listener: IMediaPlayer.OnErrorListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnErrorListener { _, what, extra ->
                listener.onError(this@MediaPlayerProxy, what, extra)
            }
        } else {
            mBackEndMediaPlayer.setOnErrorListener(null)
        }
    }

    override fun setOnInfoListener(listener: IMediaPlayer.OnInfoListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnInfoListener { _, what, extra ->
                listener.onInfo(this@MediaPlayerProxy, what, extra)
            }
        } else {
            mBackEndMediaPlayer.setOnInfoListener(null)
        }
    }

    override fun setOnTimedTextListener(listener: IMediaPlayer.OnTimedTextListener?) {
        if (listener != null) {
            mBackEndMediaPlayer.setOnTimedTextListener { _, text ->
                listener.onTimedText(this@MediaPlayerProxy, text)
            }
        } else {
            mBackEndMediaPlayer.setOnTimedTextListener(null)
        }
    }

    override fun setAudioStreamType(streamtype: Int) {
        mBackEndMediaPlayer.setAudioStreamType(streamtype)
    }

    @Deprecated("Deprecated in Java")
    override fun setKeepInBackground(keepInBackground: Boolean) {
        mBackEndMediaPlayer.setKeepInBackground(keepInBackground)
    }

    override fun getVideoSarNum(): Int = mBackEndMediaPlayer.getVideoSarNum()

    override fun getVideoSarDen(): Int = mBackEndMediaPlayer.getVideoSarDen()

    @Deprecated("Deprecated in Java")
    override fun setWakeMode(context: Context, mode: Int) {
        mBackEndMediaPlayer.setWakeMode(context, mode)
    }

    override fun getTrackInfo(): Array<out ITrackInfo>? = mBackEndMediaPlayer.getTrackInfo()

    override fun setLooping(looping: Boolean) {
        mBackEndMediaPlayer.setLooping(looping)
    }

    override fun isLooping(): Boolean = mBackEndMediaPlayer.isLooping()
}
