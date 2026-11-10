package com.github.tvbox.osc.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.github.catvod.net.OkHttp
import java.io.InputStream

@GlideModule
class OkHttpAppGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(OkHttp.client()))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}
