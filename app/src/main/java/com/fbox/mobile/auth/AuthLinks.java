package com.fbox.mobile.auth;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

public final class AuthLinks {
    private AuthLinks() { }

    /** Some plugins return a QR bitmap instead of its payload; never display the QR on a phone. */
    public static String decode(ConfigAuthProvider provider, String payload) {
        String url = payload;
        if (payload != null && payload.startsWith("data:image/") && payload.length() < 1000000) {
            Bitmap bitmap = null;
            try {
                int comma = payload.indexOf(',');
                if (comma < 0 || !payload.substring(0, comma).endsWith(";base64")) return "";
                byte[] data = Base64.decode(payload.substring(comma + 1), Base64.DEFAULT);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 2048 || options.outHeight > 2048) return "";
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap == null) return "";
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                url = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new RGBLuminanceSource(width, height, pixels)))).getText();
            } catch (Exception e) { return ""; }
            finally { if (bitmap != null) bitmap.recycle(); }
        }
        return provider.allows(url) ? url : "";
    }

    public static Intent intent(ConfigAuthProvider provider, String url, String packageName) {
        if (!provider.allows(url)) return null;
        // Do not parse intent://, copy extras, grant URI permissions, or open an app's home screen.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        if (packageName != null) {
            boolean allowed = false;
            for (String candidate : provider.packages) if (candidate.equals(packageName)) allowed = true;
            if (!allowed) return null;
            intent.setPackage(packageName);
        }
        return intent;
    }

    public static boolean openApp(Context context, ConfigAuthProvider provider, String url) {
        for (String packageName : provider.packages) {
            Intent intent = intent(provider, url, packageName);
            if (intent == null || intent.resolveActivity(context.getPackageManager()) == null) continue;
            try { context.startActivity(intent); return true; } catch (RuntimeException ignored) { }
        }
        return false;
    }

    public static boolean openWeb(Context context, ConfigAuthProvider provider, String url) {
        Intent intent = intent(provider, url, null);
        if (intent == null) return false;
        try { context.startActivity(intent); return true; } catch (RuntimeException ignored) { return false; }
    }
}
