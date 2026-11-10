package com.github.tvbox.osc.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.fragment.PlayFragment
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.ScreenUtils
import java.lang.ref.WeakReference

class MusicPlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private var sessionActivity: PendingIntent? = null
    private var title = "TVBox"
    private var subtitle = ""
    private var artworkUrl = ""
    private var artwork: Bitmap? = null
    private var position: Long = 0
    private var duration: Long = 0
    private var playing = false
    private var artworkTarget: CustomTarget<Bitmap>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "TVBoxMusic").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    getOwner()?.resumeFromMediaSession()
                }

                override fun onPause() {
                    getOwner()?.pauseFromMediaSession()
                }

                override fun onSkipToPrevious() {
                    getOwner()?.playPrevious()
                }

                override fun onSkipToNext() {
                    getOwner()?.playNext(false)
                }

                override fun onStop() {
                    getOwner()?.stopFromMediaSession()
                    stopPlaybackService()
                }

                override fun onSeekTo(pos: Long) {
                    getOwner()?.seekFromMediaSession(pos)
                }
            }, Handler(Looper.getMainLooper()))
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            sessionActivity = PendingIntent.getActivity(this, 0, launchIntent, flags)
            mediaSession?.setSessionActivity(sessionActivity)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) handleIntent(intent)
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (ACTION_STOP == action) {
            getOwner()?.stopFromMediaSession()
            stopPlaybackService()
            return
        }
        if (ACTION_PLAY == action) {
            getOwner()?.resumeFromMediaSession()
            return
        }
        if (ACTION_PAUSE == action) {
            getOwner()?.pauseFromMediaSession()
            return
        }
        if (ACTION_PREVIOUS == action) {
            getOwner()?.playPrevious()
            return
        }
        if (ACTION_NEXT == action) {
            getOwner()?.playNext(false)
            return
        }
        if (ACTION_SEEK == action) {
            getOwner()?.seekFromMediaSession(intent.getLongExtra(EXTRA_SEEK, 0))
            return
        }
        if (ACTION_UPDATE == action) {
            acquirePlaybackLocks()
            title = intent.getStringExtra(EXTRA_TITLE) ?: "TVBox"
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
            val newArtworkUrl = intent.getStringExtra(EXTRA_ARTWORK)
            position = intent.getLongExtra(EXTRA_POSITION, 0)
            duration = intent.getLongExtra(EXTRA_DURATION, 0)
            playing = intent.getBooleanExtra(EXTRA_PLAYING, false)
            updateArtwork(newArtworkUrl)
            updateSession()
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun updateArtwork(url: String?) {
        if (TextUtils.equals(artworkUrl, url)) return
        artworkUrl = url ?: ""
        artwork = null
        if (TextUtils.isEmpty(artworkUrl)) return
        if (artworkTarget != null) Glide.with(this).clear(artworkTarget)
        artworkTarget = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                artwork = resource
                updateSession()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, buildNotification())
            }

            override fun onLoadCleared(placeholder: Drawable?) {
            }
        }
        Glide.with(this).asBitmap().load(ImgUtil.getImageModel(artworkUrl)).override(256, 256).into(artworkTarget!!)
    }

    private fun updateSession() {
        val session = mediaSession ?: return
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle)
        if (duration > 0) metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (!TextUtils.isEmpty(artworkUrl)) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUrl)
        }
        if (artwork != null) metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        session.setMetadata(metadata.build())
        val action = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(action)
                .setState(state, position, if (playing) 1f else 0f)
                .build()
        )
        session.isActive = true
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }
        builder.setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setDeleteIntent(actionIntent(ACTION_STOP))
        if (mediaSession != null) {
            builder.setStyle(
                MediaStyle().setMediaSession(mediaSession!!.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }
        if (artwork != null) builder.setLargeIcon(artwork)
        builder.addAction(NotificationCompat.Action(R.drawable.media_action_placeholder, "", actionIntent(ACTION_PLACEHOLDER)))
        builder.addAction(NotificationCompat.Action(R.drawable.exo_icon_previous, "上一首", actionIntent(ACTION_PREVIOUS)))
        builder.addAction(
            NotificationCompat.Action(
                if (playing) R.drawable.exo_icon_pause else R.drawable.exo_icon_play,
                if (playing) "暂停" else "播放",
                actionIntent(if (playing) ACTION_PAUSE else ACTION_PLAY)
            )
        )
        builder.addAction(NotificationCompat.Action(R.drawable.exo_icon_next, "下一首", actionIntent(ACTION_NEXT)))
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW)
        channel.description = "音乐后台播放控制"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquirePlaybackLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TVBox:MusicPlayback")
                    wakeLock?.setReferenceCounted(false)
                }
            }
            if (wakeLock != null && !wakeLock!!.isHeld) {
                wakeLock!!.acquire()
                LOG.i("echo-music wake lock acquired")
            }
        } catch (th: Throwable) {
            LOG.i("echo-music wake lock acquire failed: " + th.message)
        }
        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TVBox:MusicPlayback")
                    wifiLock?.setReferenceCounted(false)
                }
            }
            if (wifiLock != null && !wifiLock!!.isHeld) {
                wifiLock!!.acquire()
                LOG.i("echo-music wifi lock acquired")
            }
        } catch (th: Throwable) {
            LOG.i("echo-music wifi lock acquire failed: " + th.message)
        }
    }

    private fun releasePlaybackLocks() {
        try {
            if (wifiLock != null && wifiLock!!.isHeld) {
                wifiLock!!.release()
                LOG.i("echo-music wifi lock released")
            }
        } catch (th: Throwable) {
            LOG.i("echo-music wifi lock release failed: " + th.message)
        } finally {
            wifiLock = null
        }
        try {
            if (wakeLock != null && wakeLock!!.isHeld) {
                wakeLock!!.release()
                LOG.i("echo-music wake lock released")
            }
        } catch (th: Throwable) {
            LOG.i("echo-music wake lock release failed: " + th.message)
        } finally {
            wakeLock = null
        }
    }

    private fun getOwner(): PlayFragment? {
        return owner?.get()
    }

    private fun stopPlaybackService() {
        playing = false
        releasePlaybackLocks()
        if (artworkTarget != null) Glide.with(this).clear(artworkTarget)
        if (mediaSession != null) {
            mediaSession!!.isActive = false
            mediaSession!!.release()
            mediaSession = null
        }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlaybackService()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_UPDATE = "com.github.tvbox.osc.music.UPDATE"
        private const val ACTION_PLAY = "com.github.tvbox.osc.music.PLAY"
        private const val ACTION_PAUSE = "com.github.tvbox.osc.music.PAUSE"
        private const val ACTION_PREVIOUS = "com.github.tvbox.osc.music.PREVIOUS"
        private const val ACTION_NEXT = "com.github.tvbox.osc.music.NEXT"
        private const val ACTION_PLACEHOLDER = "com.github.tvbox.osc.music.PLACEHOLDER"
        private const val ACTION_STOP = "com.github.tvbox.osc.music.STOP"
        private const val ACTION_SEEK = "com.github.tvbox.osc.music.SEEK"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_SEEK = "seek"

        private var instance: MusicPlaybackService? = null
        private var owner: WeakReference<PlayFragment>? = null

        @JvmStatic
        fun isSupported(context: Context?): Boolean {
            if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return !ScreenUtils.isTv(context)
        }

        @JvmStatic
        fun update(
            context: Context?,
            fragment: PlayFragment?,
            title: String?,
            subtitle: String?,
            artwork: String?,
            position: Long,
            duration: Long,
            playing: Boolean
        ) {
            if (!isSupported(context) || context == null) return
            owner = WeakReference(fragment)
            val intent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_UPDATE).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_ARTWORK, artwork)
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_DURATION, duration)
                putExtra(EXTRA_PLAYING, playing)
            }
            if (instance != null) {
                instance!!.handleIntent(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context?, fragment: PlayFragment?) {
            val current = owner?.get()
            if (fragment != null && current != null && current != fragment) return
            owner = null
            if (instance != null) {
                instance!!.stopPlaybackService()
            } else if (context != null) {
                context.stopService(Intent(context, MusicPlaybackService::class.java))
            }
        }
    }
}
