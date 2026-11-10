package com.fbox.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.github.tvbox.osc.ui.activity.DetailActivity

object LocalVideos {
    const val SOURCE = "local_file"

    @JvmStatic
    fun title(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (ignored: RuntimeException) {
        }
        return "本地视频"
    }

    @JvmStatic
    fun intent(context: Context, uri: Uri): Intent {
        return Intent(context, DetailActivity::class.java)
            .putExtra("id", uri.toString())
            .putExtra("sourceKey", SOURCE)
            .putExtra("title", title(context, uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
