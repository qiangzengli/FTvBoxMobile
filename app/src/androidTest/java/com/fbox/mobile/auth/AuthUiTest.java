package com.fbox.mobile.auth;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AuthUiTest {
    public static class Host extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
            return new FrameLayout(requireContext());
        }
    }

    private void show(ActivityScenario<SettingActivity> activity, ConfigAuthProvider provider,
                      AuthSession.Backend backend, Runnable refreshed) {
        activity.onActivity(host -> {
            FrameLayout container = new FrameLayout(host); container.setId(View.generateViewId());
            host.setContentView(container);
            Host fragment = new Host();
            host.getSupportFragmentManager().beginTransaction().add(container.getId(), fragment).commitNow();
            SourceBean source = new SourceBean(); source.setKey("auth-fixture"); source.setType(3); source.setApi("csp_PanConfigGuard");
            new MobileConfigAuth.LoginSheet(fragment, source, provider, false, refreshed, backend).show();
        });
    }

    private void waitForFallback() {
        long until = SystemClock.elapsedRealtime() + 5000;
        while (true) {
            try { onView(withText(containsString("没有找到可接收此授权链接的 App"))).check(matches(isDisplayed())); return; }
            catch (AssertionError | androidx.test.espresso.NoMatchingViewException e) {
                if (SystemClock.elapsedRealtime() > until) throw e;
                SystemClock.sleep(100);
            }
        }
    }

    @Test public void missingAppHasUsableFallbackAndClosingCancels() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        try (ActivityScenario<SettingActivity> activity = ActivityScenario.launch(SettingActivity.class)) {
            show(activity, ConfigAuthProvider.QUARK, (action, fields) -> {
                if (action.equals("loginStart")) return new JSONObject().put("code", 0).put("taskId", "fixture").put("qrUrl", "https://su.quark.cn/fixture");
                if (action.equals("loginCancel")) cancelled.countDown();
                return new JSONObject().put("status", "waiting");
            }, () -> fail("Opening a sheet is not authorization"));
            waitForFallback();
            onView(withText("在浏览器继续授权")).check(matches(isDisplayed()));
            onView(withText("粘贴 Cookie")).check(matches(isDisplayed()));
            onView(withText("网页登录")).check(doesNotExist());
            screenshot("native-auth-fallback.png");
            onView(withContentDescription("关闭弹窗")).perform(click());
            assertTrue(cancelled.await(3, TimeUnit.SECONDS));
        }
    }

    @Test public void manualSaveRequiresInputAndOnlyRefreshesAfterBackendAccepts() throws Exception {
        AtomicInteger writes = new AtomicInteger(); CountDownLatch refreshed = new CountDownLatch(1);
        try (ActivityScenario<SettingActivity> activity = ActivityScenario.launch(SettingActivity.class)) {
            show(activity, ConfigAuthProvider.BAIDU, (action, fields) -> {
                if (action.equals("loginStart")) return new JSONObject().put("code", 0).put("taskId", "fixture").put("qrUrl", "https://wappass.baidu.com/fixture");
                if (action.equals("setBaiduCookie")) {
                    assertEquals("BDUSS=fixture", fields.get("value")); writes.incrementAndGet();
                    return new JSONObject().put("code", 0);
                }
                return new JSONObject().put("status", "waiting");
            }, refreshed::countDown);
            waitForFallback();
            onView(withText("网页登录")).check(matches(isDisplayed()));
            onView(withText("粘贴 Cookie")).perform(click());
            onView(withText("保存登录信息")).inRoot(isDialog()).perform(scrollTo(), click());
            assertEquals(0, writes.get()); assertEquals(1, refreshed.getCount());
            onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText("BDUSS=fixture"), closeSoftKeyboard());
            onView(withText("保存登录信息")).inRoot(isDialog()).perform(scrollTo(), click());
            assertTrue(refreshed.await(5, TimeUnit.SECONDS)); assertEquals(1, writes.get());
        }
    }

    private void screenshot(String file) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) return;
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), file))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
