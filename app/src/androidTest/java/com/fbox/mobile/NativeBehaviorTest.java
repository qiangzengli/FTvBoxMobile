package com.fbox.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.TextView;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.util.HistoryHelper;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;

import androidx.lifecycle.Observer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import android.app.Activity;
import java.util.Arrays;
import java.util.ArrayList;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import fi.iki.elonen.NanoHTTPD;
import xyz.doikki.videoplayer.player.VideoView;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativeBehaviorTest {
    private FixtureServer server;
    private Context context;

    @Before public void loadFixtureConfig() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("smoke.mp4")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            server = new FixtureServer(bytes.toByteArray());
        }
        server.start();
        SourceViewModel.clearRuntimeCache();
        Hawk.put(HawkConfig.LIVE_API_URL, server.base() + "/config");
        Hawk.put(HawkConfig.API_URL, server.base() + "/config");
        Hawk.put(HawkConfig.HOME_API, "phone-fixture");
        Hawk.put(HawkConfig.HOME_REC_STYLE, true);
        Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, false);
        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override public void success() { loaded.countDown(); }
            @Override public void notice(String message) {}
            @Override public void error(String message) { error.set(message); loaded.countDown(); }
        }, null);
        assertTrue("Config load timed out", loaded.await(20, TimeUnit.SECONDS));
        assertNull(error.get());
    }

    @After public void closeServer() { if (server != null) server.stop(); }

    @Test public void webpageAndMediaUseDifferentPlaybackPaths() throws Exception {
        assertEquals(1, playback(server.base() + "/watch").getInt("parse"));
        assertEquals(0, playback(server.base() + "/video.mp4").getInt("parse"));
    }

    @Test public void homeAndSettingsFitPortraitAndKeepConfigurationControls() throws Exception {
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            await(() -> ApiConfig.get().getHomeSourceBean() != null);
            SystemClock.sleep(1000);
            home.onActivity(activity -> {
                assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.getResources().getConfiguration().orientation);
                assertNotNull(activity.findViewById(R.id.mViewPager));
                assertTrue(activity.findViewById(R.id.tvName).getWidth() > 0);
            });
            screenshot("home");
        }
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            SystemClock.sleep(500);
            settings.onActivity(activity -> {
                View api = activity.findViewById(R.id.llApi);
                assertNotNull(api);
                assertTrue(api.getWidth() <= activity.getWindow().getDecorView().getWidth());
                assertNotNull(activity.findViewById(R.id.llApiHistory));
            });
            screenshot("settings");
        }
    }

    @Test public void categorySelectionFollowsTapAndSwipe() throws Exception {
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            await(() -> homeShows(home, "手机测试源", "手机播放测试"));
            onView(withText("电影")).perform(click());
            home.onActivity(activity -> {
                androidx.viewpager.widget.ViewPager pager = activity.findViewById(R.id.mViewPager);
                assertEquals(1, pager.getCurrentItem());
                androidx.recyclerview.widget.RecyclerView tabs = activity.findViewById(R.id.mGridView);
                assertTrue(tabs.findViewHolderForAdapterPosition(1).itemView.isSelected());
            });
            screenshot("category");
            onView(withId(R.id.mViewPager)).perform(onVisibleView(swipeRight()));
            home.onActivity(activity -> {
                androidx.viewpager.widget.ViewPager pager = activity.findViewById(R.id.mViewPager);
                assertEquals(0, pager.getCurrentItem());
                androidx.recyclerview.widget.RecyclerView tabs = activity.findViewById(R.id.mGridView);
                assertTrue(tabs.findViewHolderForAdapterPosition(0).itemView.isSelected());
            });
        }
    }

    @Test public void homeHeaderFoldsAndRevealsWithVerticalContentScroll() throws Exception {
        for (boolean grid : new boolean[]{true, false}) {
            Hawk.put(HawkConfig.HOME_REC_STYLE, grid);
            try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
                await(() -> homeShows(home, "手机测试源", "手机播放测试"));
                home.onActivity(activity -> {
                    ArrayList<com.github.tvbox.osc.bean.Movie.Video> videos = new ArrayList<>();
                    for (int i = 0; i < 60; i++) {
                        com.github.tvbox.osc.bean.Movie.Video video = new com.github.tvbox.osc.bean.Movie.Video();
                        video.id = "scroll-" + i; video.name = "滚动影片 " + i; video.pic = "";
                        video.sourceKey = "phone-fixture"; videos.add(video);
                    }
                    UserFragment.homeHotVodAdapter.setNewData(videos);
                });
                final int[] sourcePosition = new int[2];
                home.onActivity(activity -> activity.findViewById(R.id.tvName).getLocationOnScreen(sourcePosition));
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true, androidx.test.espresso.action.Swipe.FAST));
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true, androidx.test.espresso.action.Swipe.FAST));
                home.onActivity(activity -> {
                    View shortcuts = activity.findViewById(R.id.mobileHomeActionsClip);
                    assertFalse("Shortcut row must fold out of the viewport", shortcuts.getGlobalVisibleRect(new android.graphics.Rect()));
                    View categories = activity.findViewById(R.id.mGridView);
                    assertFalse("Category row must fold out of the viewport", categories.getGlobalVisibleRect(new android.graphics.Rect()));
                    View pager = activity.findViewById(R.id.mViewPager);
                    assertEquals("Collapsed content fills the space below the source", 0, pager.getTop());
                    int[] current = new int[2]; activity.findViewById(R.id.tvName).getLocationOnScreen(current);
                    assertArrayEquals("Source switch stays pinned", sourcePosition, current);
                });
                screenshot(grid ? "home-folded-grid" : "home-folded-list");
                onView(withId(R.id.tvHotList)).perform(swipeVisible(false, androidx.test.espresso.action.Swipe.FAST));
                home.onActivity(activity -> {
                    assertTrue(activity.findViewById(R.id.mobileHomeActionsClip).getHeight() > 0);
                    assertTrue(((View) activity.findViewById(R.id.mGridView).getParent()).getHeight() > 0);
                });
                onView(withId(R.id.tvHistory)).check(matches(isDisplayed()));
                screenshot(grid ? "home-revealed-grid" : "home-revealed-list");
            }
        }
    }

    @Test public void homeFoldGestureKeepsTheListViewportStable() throws Exception {
        for (boolean grid : new boolean[]{true, false}) {
            Hawk.put(HawkConfig.HOME_REC_STYLE, grid);
            try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
                await(() -> homeShows(home, "手机测试源", "手机播放测试"));
                home.onActivity(activity -> {
                    ArrayList<com.github.tvbox.osc.bean.Movie.Video> catalog = new ArrayList<>();
                    for (int i = 0; i < 120; i++) {
                        com.github.tvbox.osc.bean.Movie.Video item = new com.github.tvbox.osc.bean.Movie.Video();
                        item.id = "stable" + i; item.name = "稳定滚动 " + i; catalog.add(item);
                    }
                    UserFragment.homeHotVodAdapter.setNewData(catalog);
                });
                SystemClock.sleep(700);
                final int[] measuredHeight = {0}, resizedFrames = {0}, frames = {0};
                android.view.ViewTreeObserver.OnPreDrawListener listener = () -> {
                    int height = UserFragment.tvHotList.getMeasuredHeight();
                    if (measuredHeight[0] != 0 && height != measuredHeight[0]) resizedFrames[0]++;
                    measuredHeight[0] = height; frames[0]++;
                    return true;
                };
                home.onActivity(activity -> UserFragment.tvHotList.getViewTreeObserver().addOnPreDrawListener(listener));
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true));
                onView(withId(R.id.tvHotList)).perform(swipeVisible(false));
                home.onActivity(activity -> UserFragment.tvHotList.getViewTreeObserver().removeOnPreDrawListener(listener));
                assertTrue("The gesture must render several frames", frames[0] > 3);
                assertEquals("Folding must not repeatedly resize the list and move touch coordinates", 0, resizedFrames[0]);
                screenshot(grid ? "home-stable-grid" : "home-stable-list");
            }
        }
    }

    private androidx.test.espresso.ViewAction swipeVisible(boolean upward) {
        return swipeVisible(upward, androidx.test.espresso.action.Swipe.SLOW);
    }

    private androidx.test.espresso.ViewAction swipeVisible(boolean upward, androidx.test.espresso.action.Swipe speed) {
        androidx.test.espresso.action.CoordinatesProvider start = view -> {
            android.graphics.Rect r = new android.graphics.Rect(); view.getGlobalVisibleRect(r);
            return new float[]{r.centerX(), upward ? r.bottom - r.height() * .1f : r.top + r.height() * .1f};
        };
        androidx.test.espresso.action.CoordinatesProvider end = view -> {
            android.graphics.Rect r = new android.graphics.Rect(); view.getGlobalVisibleRect(r);
            return new float[]{r.centerX(), upward ? r.top + r.height() * .1f : r.bottom - r.height() * .1f};
        };
        androidx.test.espresso.ViewAction gesture = new androidx.test.espresso.action.GeneralSwipeAction(
                speed, start, end, androidx.test.espresso.action.Press.FINGER);
        return onVisibleView(gesture);
    }

    private androidx.test.espresso.ViewAction onVisibleView(androidx.test.espresso.ViewAction gesture) {
        return new androidx.test.espresso.ViewAction() {
            @Override public org.hamcrest.Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "swipe within the visible list viewport"; }
            @Override public void perform(androidx.test.espresso.UiController ui, View view) { gesture.perform(ui, view); }
        };
    }

    @Test public void phoneSearchHistoryAndHotWordsUseFullScrollableArea() throws Exception {
        Object oldMode = Hawk.get(HawkConfig.FAST_SEARCH_MODE, true);
        ArrayList<String> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) history.add("搜索历史条目 " + i);
        history.add("很长的搜索词也应当完整布局而不能撑出手机屏幕边界的测试条目");
        Hawk.put(HawkConfig.SEARCH_HISTORY, history);
        try {
            for (boolean aggregate : new boolean[]{true, false}) {
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, aggregate);
                try (ActivityScenario<SearchActivity> search = ActivityScenario.launch(SearchActivity.class)) {
                    onView(withText("搜索历史条目 0")).check(matches(isDisplayed()));
                    onView(withId(R.id.tvClear)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
                    onView(withId(R.id.etSearch)).perform(androidx.test.espresso.action.ViewActions.replaceText("手机播放测试"), closeSoftKeyboard());
                    onView(withId(R.id.tvClear)).perform(click());
                    onView(withId(R.id.etSearch)).check(matches(withText("")));
                    await(() -> {
                        final boolean[] loaded = {false};
                        search.onActivity(activity -> {
                            androidx.recyclerview.widget.RecyclerView hot = activity.findViewById(R.id.mGridViewWord);
                            loaded[0] = hot.getAdapter().getItemCount() > 0;
                        });
                        return loaded[0];
                    });
                    search.onActivity(activity -> {
                        androidx.core.widget.NestedScrollView scroll = activity.findViewById(R.id.mobileSearchSuggestions);
                        assertTrue(scroll.getHeight() > activity.getWindow().getDecorView().getHeight() / 2);
                        scroll.fullScroll(View.FOCUS_DOWN);
                    });
                    SystemClock.sleep(400);
                    search.onActivity(activity -> {
                        androidx.recyclerview.widget.RecyclerView hot = activity.findViewById(R.id.mGridViewWord);
                        androidx.recyclerview.widget.RecyclerView.ViewHolder last = hot.findViewHolderForAdapterPosition(hot.getAdapter().getItemCount() - 1);
                        assertNotNull(last);
                        android.graphics.Rect visible = new android.graphics.Rect();
                        assertTrue(last.itemView.getGlobalVisibleRect(visible));
                        assertEquals("Last hot word must be reachable in full", last.itemView.getHeight(), visible.height());
                    });
                    screenshot(aggregate ? "search-scroll-aggregate" : "search-scroll-normal");
                    onView(withId(R.id.mobileSearchSuggestions)).perform(androidx.test.espresso.action.ViewActions.swipeDown());
                    search.onActivity(activity -> ((androidx.core.widget.NestedScrollView) activity.findViewById(R.id.mobileSearchSuggestions)).scrollTo(0, 0));
                    onView(withId(R.id.tvHistoryClear)).perform(click());
                    onView(withId(R.id.mobileSearchHistoryEmpty)).check(matches(isDisplayed()));
                    Hawk.put(HawkConfig.SEARCH_HISTORY, history);
                }
            }
        } finally { Hawk.put(HawkConfig.FAST_SEARCH_MODE, oldMode); }
    }

    @Test public void normalSearchResultsReplaceSuggestionsAndBackRestoresHistory() throws Exception {
        Hawk.put(HawkConfig.FAST_SEARCH_MODE, false);
        try (ActivityScenario<SearchActivity> search = ActivityScenario.launch(SearchActivity.class)) {
            onView(withId(R.id.etSearch)).perform(androidx.test.espresso.action.ViewActions.replaceText("手机播放测试"),
                    androidx.test.espresso.action.ViewActions.pressImeActionButton());
            await(() -> {
                final boolean[] loaded = {false};
                search.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.mGridView);
                    loaded[0] = list.isShown() && list.getAdapter().getItemCount() > 0;
                });
                return loaded[0];
            });
            onView(withId(R.id.mobileSearchSuggestions)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
            onView(withId(R.id.mobileSearchResults)).check(matches(isDisplayed()));
            screenshot("search-normal-results");
            androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.mobileSearchSuggestions)).check(matches(isDisplayed()));
            onView(withId(R.id.mobileSearchResults)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        } finally { Hawk.put(HawkConfig.FAST_SEARCH_MODE, true); }
    }

    @Test public void homeLayoutSettingKeepsBothModesVerticallyScrollable() throws Exception {
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            await(() -> homeShows(home, "手机测试源", "手机播放测试"));
            for (String mode : new String[]{"单列列表", "多列海报"}) {
                home.onActivity(activity -> activity.startActivity(new Intent(activity, SettingActivity.class)));
                onView(withId(R.id.llHomeRecStyle)).perform(scrollTo(), click());
                onView(withText(mode)).inRoot(isDialog()).perform(click());
                androidx.test.espresso.Espresso.pressBack();
                ArrayList<com.github.tvbox.osc.bean.Movie.Video> catalog = new ArrayList<>();
                for (int i = 1; i <= 20; i++) {
                    com.github.tvbox.osc.bean.Movie.Video item = new com.github.tvbox.osc.bean.Movie.Video();
                    item.id = "list-" + i; item.name = "列表影片" + i; item.pic = ""; item.sourceKey = "phone-fixture"; catalog.add(item);
                }
                home.onActivity(activity -> {
                    UserFragment.homeHotVodAdapter.setNewData(catalog);
                    androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.tvHotList);
                    androidx.recyclerview.widget.GridLayoutManager manager = (androidx.recyclerview.widget.GridLayoutManager) list.getLayoutManager();
                    assertEquals("单列列表".equals(mode) ? 1 : 3, manager.getSpanCount());
                    assertTrue(manager.canScrollVertically());
                    assertFalse(manager.canScrollHorizontally());
                    list.scrollToPosition(19);
                });
                SystemClock.sleep(400);
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true));
                onView(withText("列表影片20")).check(matches(isDisplayed()));
                screenshot("单列列表".equals(mode) ? "home-list-mode" : "home-grid-mode");
            }
        }
    }

    @Test public void phoneConfigurationPasteKeepsSeparateVodAndLiveAddresses() throws Exception {
        AtomicReference<String> saved = new AtomicReference<>();
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            settings.onActivity(activity -> {
                com.github.tvbox.osc.ui.dialog.ApiDialog dialog = new com.github.tvbox.osc.ui.dialog.ApiDialog(activity);
                dialog.setOnListener(new com.github.tvbox.osc.ui.dialog.ApiDialog.OnListener() {
                    @Override public void onchange(String api) { saved.set(api); }
                    @Override public void onLocalConfig(boolean live) {}
                });
                dialog.show();
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("config", server.base() + "/config2"));
            });
            onView(withId(R.id.mobilePasteApi)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.input)).inRoot(isDialog()).check(matches(withText(server.base() + "/config2")));
            settings.onActivity(activity -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("live", server.base() + "/live.txt"));
            });
            onView(withId(R.id.mobilePasteLive)).inRoot(isDialog()).perform(scrollTo(), click());
            onView(withId(R.id.ivQRCode)).inRoot(isDialog()).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
            screenshot("configuration-paste");
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).perform(click());
            assertEquals(server.base() + "/config2", saved.get());
            assertEquals(server.base() + "/live.txt", Hawk.get(HawkConfig.LIVE_API_URL));
        }
    }

    @Test public void linkPlaybackAcceptsPastedPhoneInput() throws Exception {
        AtomicReference<Activity> playbackActivity = new AtomicReference<>();
        try (ActivityScenario<com.github.tvbox.osc.ui.activity.PushActivity> push = ActivityScenario.launch(com.github.tvbox.osc.ui.activity.PushActivity.class)) {
            push.onActivity(activity -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("video", server.base() + "/video.mp4"));
            });
            onView(withId(R.id.pushLocal)).perform(click());
            onView(withId(R.id.mobileLinkInput)).check(matches(withText(server.base() + "/video.mp4")));
            onView(withId(R.id.mobilePlayLink)).perform(click());
            await(() -> {
                final boolean[] ready = {false};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                        if (activity instanceof DetailActivity) {
                            playbackActivity.set(activity);
                            VideoView player = activity.findViewById(R.id.mVideoView);
                            ready[0] = player != null && player.getDuration() > 0;
                        }
                    }
                });
                return ready[0];
            });
            assertTrue(server.videoRequests > 0);
        } finally {
            if (playbackActivity.get() != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> playbackActivity.get().finish());
        }
    }

    @Test public void configurationSheetSupportsPhoneKeyboard() throws Exception {
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            onView(withId(R.id.llApi)).perform(click());
            onView(withId(R.id.input)).inRoot(isDialog()).check((view, error) -> {
                if (error != null) throw error;
                assertTrue("Address input is too narrow", view.getWidth() >= MobileSheets.dp(context, 180));
            }).perform(click());
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(R.id.input)).inRoot(isDialog()).perform(androidx.test.espresso.action.ViewActions.pressImeActionButton());
            onView(withId(R.id.inputLive)).inRoot(isDialog()).check(matches(androidx.test.espresso.matcher.ViewMatchers.hasFocus()));
            screenshot("configuration-keyboard");
            onView(withId(R.id.input)).inRoot(isDialog()).perform(closeSoftKeyboard());
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).check(matches(isDisplayed()));
            screenshot("configuration-form");
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click());
        }
    }

    @Test public void localDocumentUriDecodesWithoutSubscriptionParser() throws Exception {
        File video = new File(context.getCacheDir(), "local-smoke-" + server.getListeningPort() + ".mp4");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("smoke.mp4");
             FileOutputStream out = new FileOutputStream(video)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", video);
        try (ActivityScenario<DetailActivity> detail = ActivityScenario.launch(LocalVideos.intent(context, uri))) {
            await(() -> {
                final boolean[] prepared = {false};
                detail.onActivity(activity -> {
                    VideoView player = activity.findViewById(R.id.mVideoView);
                    prepared[0] = player != null && player.getDuration() > 0 && player.getCurrentPosition() > 0;
                });
                return prepared[0];
            });
            assertEquals(0, server.videoRequests);
            screenshot("local-video");
        } finally { video.delete(); }
    }

    @Test public void phoneSearchUsesSystemInputAndFavoritesRemainAvailable() throws Exception {
        try (ActivityScenario<SearchActivity> search = ActivityScenario.launch(SearchActivity.class)) {
            SystemClock.sleep(500);
            search.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.etSearch);
                assertTrue(input.getShowSoftInputOnFocus());
                assertEquals(View.GONE, activity.findViewById(R.id.keyBoardRoot).getVisibility());
                input.setText("手机播放测试");
                assertNotNull(activity.findViewById(R.id.tvSearchCheckboxBtn));
            });
            screenshot("search");
        }
        VodInfo vod = new VodInfo();
        vod.id = "fixture";
        vod.sourceKey = "phone-fixture";
        vod.name = "手机播放测试";
        vod.pic = "";
        RoomDataManger.insertVodCollect(vod.sourceKey, vod);
        try (ActivityScenario<CollectActivity> collect = ActivityScenario.launch(CollectActivity.class)) {
            SystemClock.sleep(500);
            collect.onActivity(activity -> assertNotNull(activity.findViewById(R.id.mGridView)));
            screenshot("favorites");
        } finally {
            RoomDataManger.deleteVodCollect(vod.sourceKey, vod);
        }
    }

    @Test public void aggregatedSearchHonorsListAndPosterPreferences() throws Exception {
        for (int mode : new int[]{0, 1}) {
            Hawk.put(HawkConfig.SEARCH_VIEW, mode);
            Intent intent = new Intent(context, com.github.tvbox.osc.ui.activity.FastSearchActivity.class).putExtra("title", "手机播放测试");
            try (ActivityScenario<com.github.tvbox.osc.ui.activity.FastSearchActivity> search = ActivityScenario.launch(intent)) {
                await(() -> {
                    final boolean[] loaded = {false};
                    search.onActivity(activity -> {
                        androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.mGridView);
                        loaded[0] = list.getAdapter() != null && list.getAdapter().getItemCount() > 0;
                    });
                    return loaded[0];
                });
                search.onActivity(activity -> {
                    for (int id : new int[]{R.id.mGridView, R.id.mGridViewFilter}) {
                        androidx.recyclerview.widget.RecyclerView list = activity.findViewById(id);
                        androidx.recyclerview.widget.GridLayoutManager manager = (androidx.recyclerview.widget.GridLayoutManager) list.getLayoutManager();
                        assertEquals(mode == 0 ? 1 : 3, manager.getSpanCount());
                        assertTrue(manager.canScrollVertically());
                        assertFalse(manager.canScrollHorizontally());
                    }
                });
                search.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView sources = activity.findViewById(R.id.mGridViewWord);
                    assertTrue("Source chips must leave room for other sources", sources.getChildAt(0).getWidth() < sources.getWidth() * .75f);
                    assertNotNull(activity.findViewById(R.id.mobileSearchBack));
                });
                screenshot(mode == 0 ? "search-results-list" : "search-results-posters");
            }
        }
    }

    @Test public void nativePlayerExtractsVideoFromHtmlAndDecodesIt() throws Exception {
        Intent intent = new Intent(context, DetailActivity.class)
                .putExtra("id", server.itemId())
                .putExtra("sourceKey", "phone-fixture")
                .putExtra("title", "手机播放测试");
        try (ActivityScenario<DetailActivity> detail = ActivityScenario.launch(intent)) {
            AtomicReference<VideoView> player = new AtomicReference<>();
            await(() -> {
                detail.onActivity(activity -> player.set(activity.findViewById(R.id.mVideoView)));
                return player.get() != null;
            });
            await(() -> {
                final boolean[] prepared = {false};
                detail.onActivity(activity -> prepared[0] = player.get().getDuration() > 0);
                return prepared[0];
            });
            if (!InstrumentationRegistry.getArguments().containsKey("externalPlaybackUrl")) {
                assertTrue("The native parser did not request the video", server.videoRequests > 0);
            }
            screenshot("detail");
            detail.onActivity(activity -> activity.findViewById(R.id.tvPlay).performClick());
            await(() -> {
                final boolean[] landscape = {false};
                detail.onActivity(activity -> landscape[0] = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
                return landscape[0];
            });
            screenshot("playback");
            onView(withId(R.id.mVideoView)).perform(click());
            screenshot("player-controls");
            onView(withText("更多")).perform(click());
            screenshot("player-more");
            onView(withText("字幕")).inRoot(isDialog()).perform(scrollTo(), click());
            onView(withId(R.id.subtitleSizePlus)).inRoot(isDialog()).check(matches(isDisplayed()));
            screenshot("player-subtitle");
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.mVideoView)).perform(click());
            SystemClock.sleep(500);
            onView(withText("选集")).perform(click());
            onView(withText("第1集")).inRoot(isDialog()).check(matches(isDisplayed()));
            screenshot("player-episodes");
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click());
            detail.onActivity(activity -> activity.onBackPressed());
            await(() -> {
                final boolean[] portrait = {false};
                detail.onActivity(activity -> portrait[0] = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
                return portrait[0];
            });
            detail.onActivity(activity -> assertTrue(activity.findViewById(R.id.mGridViewFlag).isShown()));
            screenshot("playback-return");
        }
    }

    @Test public void selectingSourceRefreshesHomeRecommendations() throws Exception {
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            await(() -> homeShows(home, "手机测试源", "手机播放测试"));
            home.onActivity(activity -> activity.findViewById(R.id.tvName).performClick());
            screenshot("source-dialog");
            onView(withText("第二测试源")).inRoot(isDialog()).perform(click());
            await(() -> homeShows(home, "第二测试源", "第二线路内容"));
            screenshot("source-switched");
        }
    }

    @Test public void switchingConfigurationLineAutomaticallyReloadsHome() throws Exception {
        Hawk.put(HawkConfig.API_LINE_SOURCE, server.base() + "/collection");
        Hawk.put(HawkConfig.API_LINE_LIST, new ArrayList<>(Arrays.asList(
                HistoryHelper.buildApiLine("第一配置", server.base() + "/config"),
                HistoryHelper.buildApiLine("第二配置", server.base() + "/config2"))));
        AtomicReference<HomeActivity> newHome = new AtomicReference<>();
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            settings.onActivity(activity -> activity.findViewById(R.id.llApiLine).performClick());
            onView(withText("第二配置")).inRoot(isDialog()).perform(click());
            await(() -> {
                final boolean[] ready = {false};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                        if (activity instanceof HomeActivity) {
                            newHome.set((HomeActivity) activity);
                            ready[0] = UserFragment.homeHotVodAdapter != null
                                    && UserFragment.homeHotVodAdapter.getData().stream().anyMatch(vod -> "第二线路内容".equals(vod.name));
                        }
                    }
                });
                return ready[0];
            });
            assertEquals(server.base() + "/config2", Hawk.get(HawkConfig.API_URL));
            screenshot("config-switched");
        } finally {
            if (newHome.get() != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> newHome.get().finish());
            HistoryHelper.clearApiLineList();
        }
    }

    @Test public void liveTvLoadsChannelAndDecodesVideo() throws Exception {
        try (ActivityScenario<LivePlayActivity> live = ActivityScenario.launch(LivePlayActivity.class)) {
            await(() -> {
                final boolean[] playing = {false};
                live.onActivity(activity -> {
                    VideoView video = activity.findViewById(R.id.mVideoView);
                    playing[0] = video != null && video.getDuration() > 0;
                });
                return playing[0];
            });
            live.onActivity(activity -> assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.getResources().getConfiguration().orientation));
            screenshot("live");
            onView(withText("频道")).perform(click());
            screenshot("live-channels");
            onView(withId(R.id.tvChannelName)).check(matches(isDisplayed()));
            onView(withText("设置")).perform(click());
            screenshot("live-settings");
            onView(withId(R.id.tvRightSettingLayout)).check(matches(isDisplayed()));
        }
    }

    private boolean homeShows(ActivityScenario<HomeActivity> home, String source, String title) {
        final boolean[] matches = {false};
        home.onActivity(activity -> {
            TextView label = activity.findViewById(R.id.tvName);
            matches[0] = source.contentEquals(label.getText()) && UserFragment.homeHotVodAdapter != null
                    && UserFragment.homeHotVodAdapter.getData().stream().anyMatch(vod -> title.equals(vod.name));
        });
        return matches[0];
    }

    @Test public void importsFlutterDataOnceWithoutRemovingOriginals() throws Exception {
        SharedPreferences legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
        SharedPreferences migration = context.getSharedPreferences("native_migration", Context.MODE_PRIVATE);
        String media = "[{\"sourceKey\":\"sub::migration-source\",\"id\":\"old-id\",\"detailId\":\"detail-id\",\"title\":\"迁移测试\"}]";
        String saved = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"sub::migration-source::old-id\"]";
        legacy.edit().putString("flutter.source.current", "sub::migration-source")
                .putString("flutter.subscriptions", "[{\"id\":\"sub\",\"url\":\"https://example.test/root\",\"selectedUrl\":\"https://example.test/line\"}]")
                .putString("flutter.library.media", media).putString("flutter.library.history", saved)
                .putString("flutter.library.favorites", saved).commit();
        migration.edit().clear().commit();
        Hawk.delete(HawkConfig.API_URL);
        try {
            LegacyFlutterImport.run(context);
            assertTrue(migration.getBoolean("flutter_imported", false));
            assertEquals("https://example.test/line", Hawk.get(HawkConfig.API_URL));
            assertEquals("migration-source", Hawk.get(HawkConfig.HOME_API));
            assertEquals("https://example.test/root", Hawk.get(HawkConfig.API_LINE_SOURCE));
            assertEquals("迁移测试", RoomDataManger.getVodInfo("migration-source", "detail-id").name);
            assertTrue(RoomDataManger.isVodCollect("migration-source", "detail-id"));
            assertEquals(media, legacy.getString("flutter.library.media", ""));
            Hawk.put(HawkConfig.API_URL, "https://example.test/new-selection");
            LegacyFlutterImport.run(context);
            assertEquals("https://example.test/new-selection", Hawk.get(HawkConfig.API_URL));
        } finally {
            VodInfo vod = new VodInfo(); vod.id = "detail-id";
            RoomDataManger.deleteVodRecord("migration-source", vod);
            RoomDataManger.deleteVodCollect("migration-source", vod);
            legacy.edit().clear().commit();
            HistoryHelper.clearApiLineList();
        }
    }

    @Test public void phoneDialogsKeepFieldsAndActionsWithinScreen() throws Exception {
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            String[] layouts = {"dialog_backup", "dialog_search_subtitle", "dialog_remote", "dialog_checkbox_search", "dialog_search_remotetv", "dialog_quick_search", "dialog_tip", "dialog_episode", "dialog_confirm", "dialog_api", "dialog_cast", "dialog_subtitle", "dialog_about", "dialog_danmu_api", "dialog_desc", "dialog_api_history", "dialog_danmu_setting", "dialog_search_danmu", "dialog_select", "dialog_xwalk", "dialog_live_password", "dialog_grid_filter"};
            for (String layout : layouts) {
                AtomicReference<com.github.tvbox.osc.ui.dialog.BaseDialog> shown = new AtomicReference<>();
                settings.onActivity(activity -> {
                    com.github.tvbox.osc.ui.dialog.BaseDialog dialog = new com.github.tvbox.osc.ui.dialog.BaseDialog(activity);
                    dialog.setContentView(context.getResources().getIdentifier(layout, "layout", context.getPackageName()));
                    dialog.show(); shown.set(dialog);
                });
                SystemClock.sleep(150);
                settings.onActivity(activity -> {
                    android.view.ViewGroup decor = (android.view.ViewGroup) shown.get().getWindow().getDecorView();
                    assertTrue(layout, decor.getWidth() <= activity.getWindow().getDecorView().getWidth());
                    assertContainedHorizontally(decor, layout, false);
                });
                screenshot(layout);
                settings.onActivity(activity -> shown.get().dismiss());
            }
        }
    }

    @Test public void phoneSheetsKeepLongEpisodeListsAndFiltersUsable() throws Exception {
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            ArrayList<VodInfo.VodSeries> episodes = new ArrayList<>();
            for (int i = 1; i <= 120; i++) episodes.add(new VodInfo.VodSeries("第" + i + "集", server.base() + "/video.mp4"));
            AtomicReference<com.github.tvbox.osc.ui.dialog.EpisodeDialog> episodeDialog = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicInteger selected = new java.util.concurrent.atomic.AtomicInteger(-1);
            settings.onActivity(activity -> {
                com.github.tvbox.osc.ui.dialog.EpisodeDialog dialog = new com.github.tvbox.osc.ui.dialog.EpisodeDialog(activity, "共 120 集", episodes, 119, selected::set);
                episodeDialog.set(dialog); dialog.show();
            });
            SystemClock.sleep(800);
            settings.onActivity(activity -> {
                View decor = episodeDialog.get().getWindow().getDecorView();
                assertTrue("Episode dialog must leave room above the sheet", decor.getHeight() < activity.getWindow().getDecorView().getHeight());
            });
            onView(withText("第120集")).inRoot(isDialog()).check(matches(isDisplayed()));
            screenshot("episodes-long-list");
            onView(withText("第120集")).inRoot(isDialog()).perform(click());
            assertEquals(119, selected.get());

            com.github.tvbox.osc.bean.MovieSort.SortData category = new com.github.tvbox.osc.bean.MovieSort.SortData("movie", "电影");
            com.github.tvbox.osc.bean.MovieSort.SortFilter filter = new com.github.tvbox.osc.bean.MovieSort.SortFilter();
            filter.key = "year"; filter.name = "年份"; filter.values = new java.util.LinkedHashMap<>();
            filter.values.put("2026", "2026"); filter.values.put("2025", "2025"); category.filters.add(filter);
            final boolean[] changed = {false};
            settings.onActivity(activity -> {
                com.github.tvbox.osc.ui.dialog.GridFilterDialog dialog = new com.github.tvbox.osc.ui.dialog.GridFilterDialog(activity);
                dialog.setData(category); dialog.setOnDismiss(() -> changed[0] = true); dialog.show();
            });
            onView(withText("2026")).inRoot(isDialog()).perform(click());
            screenshot("filter-selection");
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click());
            assertTrue(changed[0]);
            assertEquals("2026", category.filterSelect.get("year"));
        }
    }

    @Test public void cancelAndSwipeDismissNeverClearFavorites() throws Exception {
        VodInfo vod = new VodInfo(); vod.id = server.itemId(); vod.name = "保留的收藏"; vod.sourceKey = "phone-fixture";
        RoomDataManger.insertVodCollect(vod.sourceKey, vod);
        try (ActivityScenario<CollectActivity> collect = ActivityScenario.launch(CollectActivity.class)) {
            collect.onActivity(activity -> activity.findViewById(R.id.tvClear).performClick());
            onView(withText("取消")).inRoot(isDialog()).perform(click());
            assertTrue(RoomDataManger.isVodCollect(vod.sourceKey, vod.id));
            collect.onActivity(activity -> activity.findViewById(R.id.tvClear).performClick());
            screenshot("confirm-clear");
            onView(withContentDescription("下滑关闭弹窗")).inRoot(isDialog()).perform(
                    new androidx.test.espresso.action.GeneralSwipeAction(androidx.test.espresso.action.Swipe.SLOW,
                            androidx.test.espresso.action.GeneralLocation.CENTER,
                            view -> { int[] location = new int[2]; view.getLocationOnScreen(location);
                                return new float[]{location[0] + view.getWidth() / 2f, location[1] + MobileSheets.dp(context, 100)}; },
                            androidx.test.espresso.action.Press.FINGER));
            onView(withContentDescription("关闭弹窗")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            assertTrue(RoomDataManger.isVodCollect(vod.sourceKey, vod.id));
        } finally { RoomDataManger.deleteVodCollect(vod.sourceKey, vod); }
    }

    private void assertContainedHorizontally(View view, String layout, boolean horizontalScroller) {
        boolean scrolling = horizontalScroller || view instanceof android.widget.HorizontalScrollView
                || view instanceof androidx.recyclerview.widget.RecyclerView;
        android.graphics.Rect visible = new android.graphics.Rect();
        if (!scrolling && view instanceof TextView && view.getVisibility() == View.VISIBLE
                && view.getGlobalVisibleRect(visible) && visible.height() > 4) {
            assertTrue(layout + ": field clipped horizontally: " + ((TextView) view).getText(),
                    visible.width() + 3 >= view.getWidth());
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) assertContainedHorizontally(parent.getChildAt(i), layout, scrolling);
        }
    }

    @Test public void phoneUtilityScreensOpenAndExposeTouchActions() throws Exception {
        try (ActivityScenario<com.github.tvbox.osc.ui.activity.PushActivity> push = ActivityScenario.launch(com.github.tvbox.osc.ui.activity.PushActivity.class)) {
            push.onActivity(activity -> assertTrue(activity.findViewById(R.id.pushLocal).hasOnClickListeners()));
            screenshot("push");
        }
        try (ActivityScenario<com.github.tvbox.osc.ui.activity.LocalFileActivity> local = ActivityScenario.launch(com.github.tvbox.osc.ui.activity.LocalFileActivity.class)) {
            local.onActivity(activity -> assertNotNull(activity.findViewById(R.id.fileList)));
            screenshot("local-files");
        }
    }

    @Test public void searchNavigationUsesTheInputRowWithoutAnotherTitleBar() throws Exception {
        try (ActivityScenario<SearchActivity> search = ActivityScenario.launch(SearchActivity.class)) {
            onView(withId(R.id.mobileSearchBack)).check(matches(isDisplayed()));
            search.onActivity(activity -> {
                View back = activity.findViewById(R.id.mobileSearchBack);
                View input = activity.findViewById(R.id.etSearch);
                int[] backAt = new int[2], inputAt = new int[2];
                back.getLocationOnScreen(backAt); input.getLocationOnScreen(inputAt);
                assertTrue(Math.abs(backAt[1] - inputAt[1]) < MobileSheets.dp(activity, 8));
                assertTrue("Search input should be at the top of the page", inputAt[1] < MobileSheets.dp(activity, 64));
            });
            screenshot("audit-search-inline-navigation");
            onView(withId(R.id.mobileSearchBack)).perform(click());
            // Activity teardown completes asynchronously after the click/transition.
            await(() -> search.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
    }

    @Test public void populatedDialogListsExposeTheLastRowWithoutClippedFields() throws Exception {
        int[][] cases = {
                {R.layout.dialog_search_subtitle, R.id.mGridView, R.layout.item_search_subtitle_result, R.id.subtitleName},
                {R.layout.dialog_search_danmu, R.id.mGridView, R.layout.item_search_danmu_result, R.id.danmuName},
                {R.layout.dialog_quick_search, R.id.mGridView, R.layout.item_quick_search_lite, R.id.tvName},
                {R.layout.dialog_backup, R.id.list, R.layout.item_dialog_backup, R.id.tvName},
                {R.layout.dialog_select, R.id.list, R.layout.item_dialog_select, R.id.tvName},
                {R.layout.dialog_api_history, R.id.list, R.layout.item_dialog_api_history, R.id.tvName},
                {R.layout.dialog_cast, R.id.list, R.layout.item_cast_device, R.id.tvDeviceName},
                {R.layout.dialog_checkbox_search, R.id.mGridView, R.layout.item_dialog_checkbox_search, R.id.oneSearchSource}
        };
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            for (int[] spec : cases) {
                AtomicReference<com.github.tvbox.osc.ui.dialog.BaseDialog> shown = new AtomicReference<>();
                String name = context.getResources().getResourceEntryName(spec[0]);
                settings.onActivity(activity -> {
                    com.github.tvbox.osc.ui.dialog.BaseDialog dialog = new com.github.tvbox.osc.ui.dialog.BaseDialog(activity);
                    dialog.setContentView(spec[0]);
                    androidx.recyclerview.widget.RecyclerView list = dialog.findViewById(spec[1]);
                    list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(activity));
                    ArrayList<String> data = new ArrayList<>();
                    for (int i = 1; i <= 40; i++) data.add("第" + i + "项 · 很长的影片及文件名称用于检查手机上的文字边界");
                    list.setAdapter(new com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder>(spec[2], data) {
                        @Override protected void convert(com.chad.library.adapter.base.BaseViewHolder holder, String value) { holder.setText(spec[3], value); }
                    });
                    View spinner = dialog.findViewById(R.id.progressBar);
                    if (spinner != null) spinner.setVisibility(View.GONE);
                    View scanning = dialog.findViewById(R.id.tvScanning);
                    if (scanning != null) scanning.setVisibility(View.GONE);
                    dialog.show(); shown.set(dialog);
                });
                SystemClock.sleep(250);
                settings.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = shown.get().findViewById(spec[1]);
                    assertTrue(name + " has no useful list viewport", list.getHeight() >= MobileSheets.dp(activity, 48));
                    list.scrollToPosition(39);
                });
                SystemClock.sleep(250);
                settings.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = shown.get().findViewById(spec[1]);
                    androidx.recyclerview.widget.RecyclerView.ViewHolder last = list.findViewHolderForAdapterPosition(39);
                    assertNotNull(name, last);
                    last.itemView.requestRectangleOnScreen(new android.graphics.Rect(0, 0, last.itemView.getWidth(), last.itemView.getHeight()), true);
                });
                SystemClock.sleep(200);
                try {
                    settings.onActivity(activity -> {
                        androidx.recyclerview.widget.RecyclerView list = shown.get().findViewById(spec[1]);
                        View row = list.findViewHolderForAdapterPosition(39).itemView;
                        android.graphics.Rect visible = new android.graphics.Rect();
                        assertTrue(name, row.getGlobalVisibleRect(visible));
                        assertEquals(name + " last row is cut off", row.getHeight(), visible.height());
                        assertContainedHorizontally(row, name, false);
                    });
                } finally {
                    screenshot("audit-filled-" + name);
                    settings.onActivity(activity -> shown.get().dismiss());
                }
            }
        }
    }

    @Test public void filterOptionsWrapAndRestoreTheSelectionWithoutStaleViews() throws Exception {
        com.github.tvbox.osc.bean.MovieSort.SortData category = new com.github.tvbox.osc.bean.MovieSort.SortData("movie", "电影");
        com.github.tvbox.osc.bean.MovieSort.SortFilter filter = new com.github.tvbox.osc.bean.MovieSort.SortFilter();
        filter.key = "area"; filter.name = "地区"; filter.values = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 36; i++) filter.values.put("地区选项" + i, "area" + i);
        category.filters.add(filter); category.filterSelect.put("area", "area1");
        try (ActivityScenario<SettingActivity> settings = ActivityScenario.launch(SettingActivity.class)) {
            AtomicReference<com.github.tvbox.osc.ui.dialog.GridFilterDialog> shown = new AtomicReference<>();
            settings.onActivity(activity -> {
                com.github.tvbox.osc.ui.dialog.GridFilterDialog dialog = new com.github.tvbox.osc.ui.dialog.GridFilterDialog(activity);
                dialog.setData(category); dialog.show(); shown.set(dialog);
            });
            onView(withText("地区选项1")).inRoot(isDialog()).check(matches(androidx.test.espresso.matcher.ViewMatchers.isSelected())).perform(click());
            assertFalse(category.filterSelect.containsKey("area"));
            settings.onActivity(activity -> {
                androidx.recyclerview.widget.RecyclerView options = shown.get().findViewById(R.id.mFilterKv);
                assertFalse(options.getLayoutManager().canScrollHorizontally());
                View last = options.findViewHolderForAdapterPosition(35).itemView;
                last.requestRectangleOnScreen(new android.graphics.Rect(0, 0, last.getWidth(), last.getHeight()), true);
            });
            onView(withText("地区选项36")).inRoot(isDialog()).perform(click());
            assertEquals("area36", category.filterSelect.get("area"));
            screenshot("audit-filter-options");
            settings.onActivity(activity -> shown.get().dismiss());
        }
    }

    @Test public void detailControlsAndLongEpisodeListsRemainReachableBelowPreview() throws Exception {
        server.episodeCount = 120;
        boolean preview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);
        Hawk.put(HawkConfig.SHOW_PREVIEW, false);
        Intent intent = new Intent(context, DetailActivity.class).putExtra("id", server.itemId())
                .putExtra("sourceKey", "phone-fixture").putExtra("title", "手机播放测试");
        try (ActivityScenario<DetailActivity> detail = ActivityScenario.launch(intent)) {
            await(() -> {
                final boolean[] loaded = {false};
                detail.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.mGridView);
                    loaded[0] = list.getAdapter() != null && list.getAdapter().getItemCount() == 120;
                });
                return loaded[0];
            });
            onView(withId(R.id.tvCollect)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.mSeriesSortTv)).perform(scrollTo()).check(matches(isDisplayed()));
            detail.onActivity(activity -> {
                androidx.recyclerview.widget.RecyclerView groups = activity.findViewById(R.id.mSeriesGroupView);
                assertTrue("Episode group hit targets must not be clipped", groups.getHeight() >= MobileSheets.dp(activity, 48));
                androidx.core.widget.NestedScrollView scroll = activity.findViewById(R.id.mobileDetailScroll);
                scroll.fullScroll(View.FOCUS_DOWN);
                ((androidx.recyclerview.widget.RecyclerView) activity.findViewById(R.id.mGridView)).scrollToPosition(119);
            });
            SystemClock.sleep(500);
            detail.onActivity(activity -> {
                androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.mGridView);
                View last = list.findViewHolderForAdapterPosition(119).itemView;
                last.requestRectangleOnScreen(new android.graphics.Rect(0, 0, last.getWidth(), last.getHeight()), true);
            });
            onView(withText("第120集")).check(matches(androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed()));
            screenshot("audit-detail-long-episodes");
        } finally { Hawk.put(HawkConfig.SHOW_PREVIEW, preview); }
    }

    private JSONObject playback(String url) throws Exception {
        AtomicReference<JSONObject> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SourceViewModel model = new SourceViewModel();
        Observer<JSONObject> observer = value -> { result.set(value); latch.countDown(); };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            model.playResult.observeForever(observer);
            model.getPlay("phone-fixture", "test", "progress", url, "subtitle");
        });
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertNotNull(result.get());
            return result.get();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.playResult.removeObserver(observer));
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        long end = SystemClock.elapsedRealtime() + 30_000;
        while (SystemClock.elapsedRealtime() < end) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        fail("Native operation did not finish within 30 seconds");
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(600); // Let window and rotation animations reach the captured frame.
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) return;
        try (FileOutputStream output = new FileOutputStream(new File(context.getCacheDir(), "native-" + name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }

    private static class FixtureServer extends NanoHTTPD {
        private final byte[] video;
        volatile int videoRequests;
        volatile int episodeCount = 1;
        private String episodes(String url) {
            ArrayList<String> values = new ArrayList<>();
            for (int i = 1; i <= episodeCount; i++) values.add("第" + i + "集$" + url);
            return android.text.TextUtils.join("#", values);
        }
        FixtureServer(byte[] video) { super("127.0.0.1", 0); this.video = video; }
        String base() { return "http://127.0.0.1:" + getListeningPort(); }
        String itemId() { return "fixture-" + getListeningPort(); }
        @Override public Response serve(IHTTPSession session) {
            String base = base();
            String webpage = InstrumentationRegistry.getArguments().getString("externalPlaybackUrl", base + "/watch");
            switch (session.getUri()) {
                case "/config":
                case "/config2":
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"sites\":[{\"key\":\"phone-fixture\",\"name\":\"手机测试源\",\"type\":1,\"api\":\"" + base + (session.getUri().equals("/config2") ? "/second" : "/api") + "\"},{\"key\":\"phone-second\",\"name\":\"第二测试源\",\"type\":1,\"api\":\"" + base + "/second\"}],\"lives\":[{\"name\":\"手机直播\",\"type\":0,\"url\":\"" + base + "/live.txt\"}]}");
                case "/live.txt":
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "测试分组,#genre#\n测试频道," + base + "/video.mp4");
                case "/watch":
                    return newFixedLengthResponse(Response.Status.OK, "text/html", "<!doctype html><html><body><video autoplay muted playsinline src='" + base + "/video.mp4'></video></body></html>");
                case "/video.mp4":
                    videoRequests++;
                    String range = session.getHeaders().get("range");
                    int start = 0;
                    if (range != null && range.startsWith("bytes=")) {
                        start = Integer.parseInt(range.substring(6).split("-")[0]);
                    }
                    start = Math.min(start, video.length - 1);
                    Response response = newFixedLengthResponse(range == null ? Response.Status.OK : Response.Status.PARTIAL_CONTENT,
                            "video/mp4", new ByteArrayInputStream(video, start, video.length - start), video.length - start);
                    response.addHeader("Accept-Ranges", "bytes");
                    if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + (video.length - 1) + "/" + video.length);
                    return response;
                default:
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"class\":[{\"type_id\":\"1\",\"type_name\":\"电影\"}],\"page\":1,\"pagecount\":1,\"list\":[{\"vod_id\":\"" + itemId() + "\",\"vod_name\":\"" + (session.getUri().equals("/second") ? "第二线路内容" : "手机播放测试") + "\",\"vod_pic\":\"\",\"vod_play_from\":\"web$$$direct\",\"vod_play_url\":\"" + episodes(webpage) + "$$$" + episodes(base + "/video.mp4") + "\"}]}");
            }
        }
    }
}
