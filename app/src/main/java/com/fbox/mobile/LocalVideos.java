package com.fbox.mobile;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.github.tvbox.osc.ui.activity.DetailActivity;

/** System document URIs stay local and bypass subscription parsers. */
public final class LocalVideos {
    public static final String SOURCE = "local_file";
    private LocalVideos() {}

    public static String title(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return "本地视频";
    }

    public static Intent intent(Context context, Uri uri) {
        return new Intent(context, DetailActivity.class)
                .putExtra("id", uri.toString()).putExtra("sourceKey", SOURCE)
                .putExtra("title", title(context, uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
