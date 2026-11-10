package com.github.tvbox.osc.player.render

import android.content.Context
import xyz.doikki.videoplayer.render.IRenderView
import xyz.doikki.videoplayer.render.RenderViewFactory

class SurfaceRenderViewFactory : RenderViewFactory() {

    companion object {
        @JvmStatic
        fun create(): SurfaceRenderViewFactory {
            return SurfaceRenderViewFactory()
        }
    }

    override fun createRenderView(context: Context): IRenderView {
        return SurfaceRenderView(context)
    }
}
