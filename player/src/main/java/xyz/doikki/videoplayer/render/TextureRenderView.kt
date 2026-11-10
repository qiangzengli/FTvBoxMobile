package xyz.doikki.videoplayer.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import xyz.doikki.videoplayer.player.AbstractPlayer

@SuppressLint("ViewConstructor")
class TextureRenderView(context: Context) : TextureView(context), IRenderView, TextureView.SurfaceTextureListener {

    private val mMeasureHelper: MeasureHelper = MeasureHelper()
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mMediaPlayer: AbstractPlayer? = null
    private var mSurface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    override fun attachToPlayer(player: AbstractPlayer) {
        this.mMediaPlayer = player
        val surface = mSurface
        if (surface != null) {
            player.setSurface(surface)
        }
    }

    override fun setVideoSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth > 0 && videoHeight > 0) {
            mMeasureHelper.setVideoSize(videoWidth, videoHeight)
            requestLayout()
        }
    }

    override fun setVideoRotation(degree: Int) {
        mMeasureHelper.setVideoRotation(degree)
        rotation = degree.toFloat()
    }

    override fun setScaleType(scaleType: Int) {
        mMeasureHelper.setScreenScale(scaleType)
        requestLayout()
    }

    override fun getView(): View {
        return this
    }

    override fun doScreenShot(): Bitmap? {
        return bitmap
    }

    override fun release() {
        mSurface?.release()
        mSurfaceTexture?.release()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredSize = mMeasureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredSize[0], measuredSize[1])
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val existingTexture = mSurfaceTexture
        if (existingTexture != null) {
            setSurfaceTexture(existingTexture)
        } else {
            mSurfaceTexture = surfaceTexture
            val surface = Surface(surfaceTexture)
            mSurface = surface
            mMediaPlayer?.setSurface(surface)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}
