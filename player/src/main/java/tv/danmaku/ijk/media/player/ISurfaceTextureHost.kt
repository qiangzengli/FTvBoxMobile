package tv.danmaku.ijk.media.player

import android.graphics.SurfaceTexture

interface ISurfaceTextureHost {
    fun releaseSurfaceTexture(surfaceTexture: SurfaceTexture?)
}
