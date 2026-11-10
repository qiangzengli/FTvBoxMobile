package xyz.doikki.videoplayer.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * 音频焦点改变监听
 */
class AudioFocusHelper(videoView: VideoView<*>) : AudioManager.OnAudioFocusChangeListener {

    private val mHandler = Handler(Looper.getMainLooper())
    private val mWeakVideoView = WeakReference(videoView)
    private val mAudioManager: AudioManager? =
        videoView.context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var mStartRequested = false
    private var mPausedForLoss = false
    private var mCurrentFocus = 0

    override fun onAudioFocusChange(focusChange: Int) {
        if (mCurrentFocus == focusChange) {
            return
        }

        mHandler.post {
            handleAudioFocusChange(focusChange)
        }

        mCurrentFocus = focusChange
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        val videoView = mWeakVideoView.get() ?: return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                if (mStartRequested || mPausedForLoss) {
                    videoView.start()
                    mStartRequested = false
                    mPausedForLoss = false
                }
                if (!videoView.isMute()) {
                    videoView.setVolume(1.0f, 1.0f)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (videoView.isPlaying()) {
                    mPausedForLoss = true
                    videoView.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (videoView.isPlaying() && !videoView.isMute()) {
                    videoView.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    fun requestFocus() {
        if (mCurrentFocus == AudioManager.AUDIOFOCUS_GAIN) {
            return
        }

        val audioManager = mAudioManager ?: return

        val status = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status) {
            mCurrentFocus = AudioManager.AUDIOFOCUS_GAIN
            return
        }

        mStartRequested = true
    }

    fun abandonFocus() {
        val audioManager = mAudioManager ?: return
        mStartRequested = false
        audioManager.abandonAudioFocus(this)
    }
}
