package com.fbox.mobile.auth

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.ui.activity.SettingActivity
import org.hamcrest.Matchers.containsString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AuthUiTest {
    class Host : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
            return FrameLayout(requireContext())
        }
    }

    private fun show(
        activity: ActivityScenario<SettingActivity>,
        provider: ConfigAuthProvider,
        backend: AuthSession.Backend,
        refreshed: Runnable
    ) {
        activity.onActivity { host ->
            val container = FrameLayout(host)
            container.id = View.generateViewId()
            host.setContentView(container)
            val fragment = Host()
            host.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
            val source = SourceBean()
            source.key = "auth-fixture"
            source.type = 3
            source.api = "csp_PanConfigGuard"
            MobileConfigAuth.LoginSheet(fragment, source, provider, false, refreshed, backend).show()
        }
    }

    private fun waitForFallback() {
        val until = SystemClock.elapsedRealtime() + 5000
        while (true) {
            try {
                onView(withText(containsString("没有找到可接收此授权链接的 App"))).check(matches(isDisplayed()))
                return
            } catch (e: AssertionError) {
                if (SystemClock.elapsedRealtime() > until) throw e
                SystemClock.sleep(100)
            } catch (e: androidx.test.espresso.NoMatchingViewException) {
                if (SystemClock.elapsedRealtime() > until) throw e
                SystemClock.sleep(100)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun missingAppHasUsableFallbackAndClosingCancels() {
        val cancelled = CountDownLatch(1)
        ActivityScenario.launch(SettingActivity::class.java).use { activity ->
            show(activity, ConfigAuthProvider.QUARK, { action, _ ->
                if (action == "loginStart") return@show JSONObject().put("code", 0).put("taskId", "fixture").put("qrUrl", "https://su.quark.cn/fixture")
                if (action == "loginCancel") cancelled.countDown()
                JSONObject().put("status", "waiting")
            }, { fail("Opening a sheet is not authorization") })
            waitForFallback()
            onView(withText("在浏览器继续授权")).check(matches(isDisplayed()))
            onView(withText("粘贴 Cookie")).check(matches(isDisplayed()))
            onView(withText("网页登录")).check(doesNotExist())
            screenshot("native-auth-fallback.png")
            onView(withContentDescription("关闭弹窗")).perform(click())
            assertTrue(cancelled.await(3, TimeUnit.SECONDS))
        }
    }

    @Test
    @Throws(Exception::class)
    fun manualSaveRequiresInputAndOnlyRefreshesAfterBackendAccepts() {
        val writes = AtomicInteger()
        val refreshed = CountDownLatch(1)
        ActivityScenario.launch(SettingActivity::class.java).use { activity ->
            show(activity, ConfigAuthProvider.BAIDU, { action, fields ->
                if (action == "loginStart") return@show JSONObject().put("code", 0).put("taskId", "fixture").put("qrUrl", "https://wappass.baidu.com/fixture")
                if (action == "setBaiduCookie") {
                    assertEquals("BDUSS=fixture", fields["value"])
                    writes.incrementAndGet()
                    return@show JSONObject().put("code", 0)
                }
                JSONObject().put("status", "waiting")
            }, { refreshed.countDown() })
            waitForFallback()
            onView(withText("网页登录")).check(matches(isDisplayed()))
            onView(withText("粘贴 Cookie")).perform(click())
            onView(withText("保存登录信息")).inRoot(isDialog()).perform(scrollTo(), click())
            assertEquals(0, writes.get())
            assertEquals(1, refreshed.count)
            onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("BDUSS=fixture"), closeSoftKeyboard())
            onView(withText("保存登录信息")).inRoot(isDialog()).perform(scrollTo(), click())
            assertTrue(refreshed.await(5, TimeUnit.SECONDS))
            assertEquals(1, writes.get())
        }
    }

    @Throws(Exception::class)
    private fun screenshot(file: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        try {
            FileOutputStream(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, file)).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
