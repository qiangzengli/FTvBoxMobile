package tv.danmaku.ijk.media.player

import android.graphics.SurfaceTexture

interface ISurfaceTextureHolder {
    fun setSurfaceTexture(surfaceTexture: SurfaceTexture?)
    fun getSurfaceTexture(): SurfaceTexture?
    fun setSurfaceTextureHost(surfaceTextureHost: ISurfaceTextureHost?)
}
