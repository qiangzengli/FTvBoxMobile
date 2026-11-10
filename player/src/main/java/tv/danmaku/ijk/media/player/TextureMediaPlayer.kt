package tv.danmaku.ijk.media.player

import android.annotation.TargetApi
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class TextureMediaPlayer(backEndMediaPlayer: IMediaPlayer) : MediaPlayerProxy(backEndMediaPlayer), IMediaPlayer, ISurfaceTextureHolder {
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mSurfaceTextureHost: ISurfaceTextureHost? = null

    fun releaseSurfaceTexture() {
        if (mSurfaceTexture != null) {
            if (mSurfaceTextureHost != null) {
                mSurfaceTextureHost?.releaseSurfaceTexture(mSurfaceTexture)
            } else {
                mSurfaceTexture?.release()
            }
            mSurfaceTexture = null
        }
    }

    override fun reset() {
        super.reset()
        releaseSurfaceTexture()
    }

    override fun release() {
        super.release()
        releaseSurfaceTexture()
    }

    override fun setDisplay(sh: SurfaceHolder?) {
        if (mSurfaceTexture == null) {
            super.setDisplay(sh)
        }
    }

    override fun setSurface(surface: Surface?) {
        if (mSurfaceTexture == null) {
            super.setSurface(surface)
        }
    }

    override fun setSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        if (mSurfaceTexture === surfaceTexture) return

        releaseSurfaceTexture()
        mSurfaceTexture = surfaceTexture
        if (surfaceTexture == null) {
            super.setSurface(null)
        } else {
            super.setSurface(Surface(surfaceTexture))
        }
    }

    override fun getSurfaceTexture(): SurfaceTexture? = mSurfaceTexture

    override fun setSurfaceTextureHost(surfaceTextureHost: ISurfaceTextureHost?) {
        mSurfaceTextureHost = surfaceTextureHost
    }
}
