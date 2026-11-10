package com.fbox.mobile

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fbox.mobile.auth.AuthLinks
import com.fbox.mobile.auth.AuthSession
import com.fbox.mobile.auth.ConfigAuthProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AuthSessionTest {
    companion object {
        @Throws(Exception::class)
        private fun started(): JSONObject {
            return JSONObject().put("code", 0).put("taskId", "fixture-task").put("qrUrl", "https://su.quark.cn/fixture")
        }
    }

    private class Listener : AuthSession.Listener {
        val ready = AtomicInteger()
        val success = AtomicInteger()
        val failed = AtomicInteger()
        val terminal = CountDownLatch(1)

        override fun ready(url: String) {
            ready.incrementAndGet()
        }

        override fun waiting() {}

        override fun success() {
            success.incrementAndGet()
            terminal.countDown()
        }

        override fun failed(message: String) {
            failed.incrementAndGet()
            terminal.countDown()
        }
    }

    @Test
    @Throws(Exception::class)
    fun openingLinkDoesNotSucceedUntilPluginConfirms() {
        val listener = Listener()
        val polls = AtomicInteger()
        val worker = Executors.newSingleThreadScheduledExecutor()
        val session = AuthSession({ action, fields ->
            assertEquals("quark", fields["provider"])
            if (action == "loginStart") return@AuthSession started()
            assertEquals("fixture-task", fields["taskId"])
            assertEquals(0, listener.success.get())
            JSONObject().put("status", if (polls.incrementAndGet() < 2) "waiting" else "success")
        }, "quark", worker, { it.run() }, listener, 20, 3000)
        try {
            session.start()
            session.start()
            assertTrue(listener.terminal.await(3, TimeUnit.SECONDS))
            assertEquals(1, listener.ready.get())
            assertEquals(1, listener.success.get())
            assertEquals(2, polls.get())
            assertEquals(0, listener.failed.get())
        } finally {
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    @Throws(Exception::class)
    fun closingDuringStartCancelsLateSessionWithoutOpeningApp() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val listener = Listener()
        val worker = Executors.newSingleThreadScheduledExecutor()
        val session = AuthSession({ action, fields ->
            if (action == "loginStart") {
                entered.countDown()
                assertTrue(release.await(3, TimeUnit.SECONDS))
                return@AuthSession started()
            }
            assertEquals("loginCancel", action)
            assertEquals("fixture-task", fields["taskId"])
            cancelled.countDown()
            JSONObject()
        }, "quark", worker, { it.run() }, listener, 20, 3000)
        try {
            session.start()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            session.close()
            release.countDown()
            assertTrue(cancelled.await(3, TimeUnit.SECONDS))
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS))
            assertEquals(0, listener.ready.get())
            assertEquals(0, listener.success.get())
        } finally {
            release.countDown()
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    @Throws(Exception::class)
    fun dismissalSuppressesAlreadyQueuedCallbacks() {
        val callbacks = Collections.synchronizedList(ArrayList<Runnable>())
        val enqueued = CountDownLatch(1)
        val listener = Listener()
        val worker = Executors.newSingleThreadScheduledExecutor()
        val session = AuthSession(
            { action, _ -> if (action == "loginStart") started() else JSONObject() },
            "quark",
            worker,
            { callback ->
                callbacks.add(callback)
                enqueued.countDown()
            },
            listener,
            1000,
            3000
        )
        try {
            session.start()
            assertTrue(enqueued.await(3, TimeUnit.SECONDS))
            session.close()
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS))
            for (callback in callbacks) callback.run()
            assertEquals(0, listener.ready.get())
            assertEquals(0, listener.success.get())
        } finally {
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    @Throws(Exception::class)
    fun expiredSessionsAndNetworkFailuresStopAndCancel() {
        for (networkError in booleanArrayOf(false, true)) {
            val listener = Listener()
            val cancels = AtomicInteger()
            val polls = AtomicInteger()
            val worker = Executors.newSingleThreadScheduledExecutor()
            val session = AuthSession({ action, _ ->
                if (action == "loginStart") return@AuthSession started()
                if (action == "loginCancel") {
                    cancels.incrementAndGet()
                    return@AuthSession JSONObject()
                }
                polls.incrementAndGet()
                if (networkError) throw IOException("fixture")
                JSONObject().put("status", "expired").put("terminal", true)
            }, "quark", worker, { it.run() }, listener, 20, 3000)
            try {
                session.start()
                assertTrue(listener.terminal.await(3, TimeUnit.SECONDS))
                assertEquals(0, listener.success.get())
                assertEquals(1, listener.failed.get())
                assertEquals(1, cancels.get())
                assertEquals(if (networkError) 3 else 1, polls.get())
            } finally {
                session.close()
                worker.shutdownNow()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun malformedStartIsNotAnAuthorization() {
        val listener = Listener()
        val worker = Executors.newSingleThreadScheduledExecutor()
        val session = AuthSession(
            { _, _ -> JSONObject().put("code", 0) },
            "quark",
            worker,
            { it.run() },
            listener,
            20,
            3000
        )
        try {
            session.start()
            assertTrue(listener.terminal.await(3, TimeUnit.SECONDS))
            assertEquals(0, listener.ready.get())
            assertEquals(0, listener.success.get())
            assertEquals(1, listener.failed.get())
        } finally {
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    @Throws(Exception::class)
    fun localDeadlineCancelsAnUnfinishedSession() {
        val listener = Listener()
        val cancels = AtomicInteger()
        val worker = Executors.newSingleThreadScheduledExecutor()
        val session = AuthSession({ action, _ ->
            if (action == "loginStart") return@AuthSession started()
            assertEquals("loginCancel", action)
            cancels.incrementAndGet()
            JSONObject()
        }, "quark", worker, { it.run() }, listener, 20, 0)
        try {
            session.start()
            assertTrue(listener.terminal.await(3, TimeUnit.SECONDS))
            assertEquals(0, listener.success.get())
            assertEquals(1, listener.failed.get())
            assertEquals(1, cancels.get())
        } finally {
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    @Throws(Exception::class)
    fun safeIntentsAndEmbeddedQrAreSupported() {
        val url = "https://su.uc.cn/fixture?token=example"
        val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 256, 256)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) -0x1000000 else -0x1)
            }
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        assertEquals(
            url,
            AuthLinks.decode(
                ConfigAuthProvider.UC_TOKEN,
                "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            )
        )
        assertEquals("", AuthLinks.decode(ConfigAuthProvider.BAIDU, url))
        assertEquals("", AuthLinks.decode(ConfigAuthProvider.UC, "data:image/png;base64,broken"))
        assertNull(AuthLinks.intent(ConfigAuthProvider.UC, url, "com.evil"))
        assertNull(AuthLinks.intent(ConfigAuthProvider.UC, "intent://bad", null))
        val intent = AuthLinks.intent(ConfigAuthProvider.UC, url, "com.UCMobile")
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertTrue(intent?.hasCategory(Intent.CATEGORY_BROWSABLE) == true)
        assertEquals("com.UCMobile", intent?.`package`)
        assertNull(intent?.component)
        assertNull(intent?.extras)
        assertEquals(0, intent?.flags)
    }
}
