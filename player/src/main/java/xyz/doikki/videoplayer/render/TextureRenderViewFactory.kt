package xyz.doikki.videoplayer.render

import android.content.Context

class TextureRenderViewFactory : RenderViewFactory() {

    companion object {
        @JvmStatic
        fun create(): TextureRenderViewFactory {
            return TextureRenderViewFactory()
        }
    }

    override fun createRenderView(context: Context): IRenderView {
        return TextureRenderView(context)
    }
}
