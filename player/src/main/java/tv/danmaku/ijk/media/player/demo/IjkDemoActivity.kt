package tv.danmaku.ijk.media.player.demo

import android.app.Activity
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import com.github.tvbox.osc.player.R
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class IjkDemoActivity : Activity() {

    private var texture_view: TextureView? = null
    private var surface: Surface? = null
    private var ijkMediaPlayer: IjkMediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.ijk_demo_activity)
        findViewById<View>(R.id.button)?.setOnClickListener { v ->
            startPlay(v)
        }
        texture_view = findViewById(R.id.texture_view)
        texture_view?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                this@IjkDemoActivity.surface = Surface(surfaceTexture)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    fun startPlay(view: View?) {
        val currentSurface = surface
        if (currentSurface == null) {
            Log.d(TAG, "null == surface")
            return
        }
        val player = ijkMediaPlayer?.apply { reset() } ?: IjkMediaPlayer().also { ijkMediaPlayer = it }
        try {
            player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 200)
            player.setSurface(currentSurface)
            player.setDataSource("https://cache.tkys.tv/m3u8/dsj/guochan/mp1/1.m3u8")
            player.setOnPreparedListener { iMediaPlayer ->
                iMediaPlayer.start()
            }
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        ijkMediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ijkMediaPlayer?.let {
            it.stop()
            it.release()
        }
    }

    companion object {
        private const val TAG = "IjkDemoActivity"
    }
}
