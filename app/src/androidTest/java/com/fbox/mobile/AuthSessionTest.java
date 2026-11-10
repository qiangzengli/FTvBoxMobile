package com.fbox.mobile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Base64;
import com.fbox.mobile.auth.AuthLinks;
import com.fbox.mobile.auth.AuthSession;
import com.fbox.mobile.auth.ConfigAuthProvider;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AuthSessionTest {
    private static JSONObject started() throws Exception {
        return new JSONObject().put("code", 0).put("taskId", "fixture-task").put("qrUrl", "https://su.quark.cn/fixture");
    }

    private static class Listener implements AuthSession.Listener {
        final AtomicInteger ready = new AtomicInteger(), success = new AtomicInteger(), failed = new AtomicInteger();
        final CountDownLatch terminal = new CountDownLatch(1);
        @Override public void ready(String url) { ready.incrementAndGet(); }
        @Override public void waiting() { }
        @Override public void success() { success.incrementAndGet(); terminal.countDown(); }
        @Override public void failed(String message) { failed.incrementAndGet(); terminal.countDown(); }
    }

    @Test public void openingLinkDoesNotSucceedUntilPluginConfirms() throws Exception {
        Listener listener = new Listener(); AtomicInteger polls = new AtomicInteger();
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AuthSession session = new AuthSession((action, fields) -> {
            assertEquals("quark", fields.get("provider"));
            if (action.equals("loginStart")) return started();
            assertEquals("fixture-task", fields.get("taskId"));
            assertEquals(0, listener.success.get());
            return new JSONObject().put("status", polls.incrementAndGet() < 2 ? "waiting" : "success");
        }, "quark", worker, Runnable::run, listener, 20, 3000);
        try {
            session.start(); session.start();
            assertTrue(listener.terminal.await(3, TimeUnit.SECONDS));
            assertEquals(1, listener.ready.get()); assertEquals(1, listener.success.get());
            assertEquals(2, polls.get()); assertEquals(0, listener.failed.get());
        } finally { session.close(); worker.shutdownNow(); }
    }

    @Test public void closingDuringStartCancelsLateSessionWithoutOpeningApp() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        Listener listener = new Listener(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AuthSession session = new AuthSession((action, fields) -> {
            if (action.equals("loginStart")) { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return started(); }
            assertEquals("loginCancel", action); assertEquals("fixture-task", fields.get("taskId")); cancelled.countDown(); return new JSONObject();
        }, "quark", worker, Runnable::run, listener, 20, 3000);
        try {
            session.start(); assertTrue(entered.await(3, TimeUnit.SECONDS)); session.close(); release.countDown();
            assertTrue(cancelled.await(3, TimeUnit.SECONDS)); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
            assertEquals(0, listener.ready.get()); assertEquals(0, listener.success.get());
        } finally { release.countDown(); session.close(); worker.shutdownNow(); }
    }

    @Test public void dismissalSuppressesAlreadyQueuedCallbacks() throws Exception {
        List<Runnable> callbacks = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch enqueued = new CountDownLatch(1); Listener listener = new Listener();
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AuthSession session = new AuthSession((action, fields) -> action.equals("loginStart") ? started() : new JSONObject(),
                "quark", worker, callback -> { callbacks.add(callback); enqueued.countDown(); }, listener, 1000, 3000);
        try {
            session.start(); assertTrue(enqueued.await(3, TimeUnit.SECONDS)); session.close();
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
            for (Runnable callback : callbacks) callback.run();
            assertEquals(0, listener.ready.get()); assertEquals(0, listener.success.get());
        } finally { session.close(); worker.shutdownNow(); }
    }

    @Test public void expiredSessionsAndNetworkFailuresStopAndCancel() throws Exception {
        for (boolean networkError : new boolean[]{false, true}) {
            Listener listener = new Listener(); AtomicInteger cancels = new AtomicInteger(), polls = new AtomicInteger();
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
            AuthSession session = new AuthSession((action, fields) -> {
                if (action.equals("loginStart")) return started();
                if (action.equals("loginCancel")) { cancels.incrementAndGet(); return new JSONObject(); }
                polls.incrementAndGet();
                if (networkError) throw new java.io.IOException("fixture");
                return new JSONObject().put("status", "expired").put("terminal", true);
            }, "quark", worker, Runnable::run, listener, 20, 3000);
            try {
                session.start(); assertTrue(listener.terminal.await(3, TimeUnit.SECONDS));
                assertEquals(0, listener.success.get()); assertEquals(1, listener.failed.get());
                assertEquals(1, cancels.get()); assertEquals(networkError ? 3 : 1, polls.get());
            } finally { session.close(); worker.shutdownNow(); }
        }
    }

    @Test public void malformedStartIsNotAnAuthorization() throws Exception {
        Listener listener = new Listener(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AuthSession session = new AuthSession((action, fields) -> new JSONObject().put("code", 0),
                "quark", worker, Runnable::run, listener, 20, 3000);
        try {
            session.start(); assertTrue(listener.terminal.await(3, TimeUnit.SECONDS));
            assertEquals(0, listener.ready.get()); assertEquals(0, listener.success.get()); assertEquals(1, listener.failed.get());
        } finally { session.close(); worker.shutdownNow(); }
    }

    @Test public void localDeadlineCancelsAnUnfinishedSession() throws Exception {
        Listener listener = new Listener(); AtomicInteger cancels = new AtomicInteger();
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AuthSession session = new AuthSession((action, fields) -> {
            if (action.equals("loginStart")) return started();
            assertEquals("loginCancel", action); cancels.incrementAndGet(); return new JSONObject();
        }, "quark", worker, Runnable::run, listener, 20, 0);
        try {
            session.start(); assertTrue(listener.terminal.await(3, TimeUnit.SECONDS));
            assertEquals(0, listener.success.get()); assertEquals(1, listener.failed.get()); assertEquals(1, cancels.get());
        } finally { session.close(); worker.shutdownNow(); }
    }

    @Test public void safeIntentsAndEmbeddedQrAreSupported() throws Exception {
        String url = "https://su.uc.cn/fixture?token=example";
        BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 256, 256);
        Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < 256; x++) for (int y = 0; y < 256; y++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle();
        assertEquals(url, AuthLinks.decode(ConfigAuthProvider.UC_TOKEN, "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)));
        assertEquals("", AuthLinks.decode(ConfigAuthProvider.BAIDU, url));
        assertEquals("", AuthLinks.decode(ConfigAuthProvider.UC, "data:image/png;base64,broken"));
        assertNull(AuthLinks.intent(ConfigAuthProvider.UC, url, "com.evil"));
        assertNull(AuthLinks.intent(ConfigAuthProvider.UC, "intent://bad", null));
        Intent intent = AuthLinks.intent(ConfigAuthProvider.UC, url, "com.UCMobile");
        assertEquals(Intent.ACTION_VIEW, intent.getAction()); assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals("com.UCMobile", intent.getPackage()); assertNull(intent.getComponent()); assertNull(intent.getExtras()); assertEquals(0, intent.getFlags());
    }
}
