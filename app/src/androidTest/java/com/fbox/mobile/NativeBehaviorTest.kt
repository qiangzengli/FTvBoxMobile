package com.fbox.mobile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.viewpager.widget.ViewPager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.HomeActivity
import com.github.tvbox.osc.ui.activity.LivePlayActivity
import com.github.tvbox.osc.ui.activity.LocalFileActivity
import com.github.tvbox.osc.ui.activity.PushActivity
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.dialog.ApiDialog
import com.github.tvbox.osc.ui.dialog.BaseDialog
import com.github.tvbox.osc.ui.dialog.EpisodeDialog
import com.github.tvbox.osc.ui.dialog.GridFilterDialog
import com.github.tvbox.osc.ui.fragment.UserFragment
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import fi.iki.elonen.NanoHTTPD
import org.hamcrest.Matcher
import org.hamcrest.Matchers.not
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.doikki.videoplayer.player.VideoView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier

@RunWith(AndroidJUnit4::class)
class NativeBehaviorTest {
    private var server: FixtureServer? = null
    private lateinit var context: Context

    @Before
    @Throws(Exception::class)
    fun loadFixtureConfig() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().context.assets.open("smoke.mp4").use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var count: Int
            while (input.read(buffer).also { count = it } >= 0) bytes.write(buffer, 0, count)
            server = FixtureServer(bytes.toByteArray())
        }
        server?.start()
        SourceViewModel.clearRuntimeCache()
        val s = server!!
        Hawk.put(HawkConfig.LIVE_API_URL, s.base() + "/config")
        Hawk.put(HawkConfig.API_URL, s.base() + "/config")
        Hawk.put(HawkConfig.HOME_API, "phone-fixture")
        Hawk.put(HawkConfig.HOME_REC_STYLE, true)
        Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, false)
        val loaded = CountDownLatch(1)
        val error = AtomicReference<String>()
        ApiConfig.get().loadConfig(false, object : ApiConfig.LoadConfigCallback {
            override fun success() {
                loaded.countDown()
            }

            override fun notice(message: String) {}

            override fun error(message: String) {
                error.set(message)
                loaded.countDown()
            }
        }, null)
        assertTrue("Config load timed out", loaded.await(20, TimeUnit.SECONDS))
        assertNull(error.get())
    }

    @After
    fun closeServer() {
        server?.stop()
    }

    @Test
    @Throws(Exception::class)
    fun webpageAndMediaUseDifferentPlaybackPaths() {
        val s = server!!
        assertEquals(1, playback(s.base() + "/watch").getInt("parse"))
        assertEquals(0, playback(s.base() + "/video.mp4").getInt("parse"))
    }

    @Test
    @Throws(Exception::class)
    fun homeAndSettingsFitPortraitAndKeepConfigurationControls() {
        ActivityScenario.launch(HomeActivity::class.java).use { home ->
            await(BooleanSupplier { ApiConfig.get().getHomeSourceBean() != null })
            SystemClock.sleep(1000)
            home.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                assertNotNull(activity.findViewById(R.id.mViewPager))
                assertTrue(activity.findViewById<View>(R.id.tvName).width > 0)
            }
            screenshot("home")
        }
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            SystemClock.sleep(500)
            settings.onActivity { activity ->
                val api = activity.findViewById<View>(R.id.llApi)
                assertNotNull(api)
                assertTrue(api.width <= activity.window.decorView.width)
                assertNotNull(activity.findViewById(R.id.llApiHistory))
            }
            screenshot("settings")
        }
    }

    @Test
    @Throws(Exception::class)
    fun categorySelectionFollowsTapAndSwipe() {
        ActivityScenario.launch(HomeActivity::class.java).use { home ->
            await(BooleanSupplier { homeShows(home, "手机测试源", "手机播放测试") })
            onView(withText("电影")).perform(click())
            home.onActivity { activity ->
                val pager = activity.findViewById<ViewPager>(R.id.mViewPager)
                assertEquals(1, pager.currentItem)
                val tabs = activity.findViewById<RecyclerView>(R.id.mGridView)
                assertTrue(tabs.findViewHolderForAdapterPosition(1)!!.itemView.isSelected)
            }
            screenshot("category")
            onView(withId(R.id.mViewPager)).perform(onVisibleView(swipeRight()))
            home.onActivity { activity ->
                val pager = activity.findViewById<ViewPager>(R.id.mViewPager)
                assertEquals(0, pager.currentItem)
                val tabs = activity.findViewById<RecyclerView>(R.id.mGridView)
                assertTrue(tabs.findViewHolderForAdapterPosition(0)!!.itemView.isSelected)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun homeHeaderFoldsAndRevealsWithVerticalContentScroll() {
        for (grid in booleanArrayOf(true, false)) {
            Hawk.put(HawkConfig.HOME_REC_STYLE, grid)
            ActivityScenario.launch(HomeActivity::class.java).use { home ->
                await(BooleanSupplier { homeShows(home, "手机测试源", "手机播放测试") })
                home.onActivity {
                    val videos = ArrayList<Movie.Video>()
                    for (i in 0 until 60) {
                        val video = Movie.Video()
                        video.id = "scroll-$i"
                        video.name = "滚动影片 $i"
                        video.pic = ""
                        video.sourceKey = "phone-fixture"
                        videos.add(video)
                    }
                    UserFragment.homeHotVodAdapter?.setNewData(videos)
                }
                val sourcePosition = IntArray(2)
                home.onActivity { activity -> activity.findViewById<View>(R.id.tvName).getLocationOnScreen(sourcePosition) }
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true, Swipe.FAST))
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true, Swipe.FAST))
                home.onActivity { activity ->
                    val shortcuts = activity.findViewById<View>(R.id.mobileHomeActionsClip)
                    assertFalse("Shortcut row must fold out of the viewport", shortcuts.getGlobalVisibleRect(Rect()))
                    val categories = activity.findViewById<View>(R.id.mGridView)
                    assertFalse("Category row must fold out of the viewport", categories.getGlobalVisibleRect(Rect()))
                    val pager = activity.findViewById<View>(R.id.mViewPager)
                    assertEquals("Collapsed content fills the space below the source", 0, pager.top)
                    val current = IntArray(2)
                    activity.findViewById<View>(R.id.tvName).getLocationOnScreen(current)
                    assertArrayEquals("Source switch stays pinned", sourcePosition, current)
                }
                screenshot(if (grid) "home-folded-grid" else "home-folded-list")
                onView(withId(R.id.tvHotList)).perform(swipeVisible(false, Swipe.FAST))
                home.onActivity { activity ->
                    assertTrue(activity.findViewById<View>(R.id.mobileHomeActionsClip).height > 0)
                    assertTrue((activity.findViewById<View>(R.id.mGridView).parent as View).height > 0)
                }
                onView(withId(R.id.tvHistory)).check(matches(isDisplayed()))
                screenshot(if (grid) "home-revealed-grid" else "home-revealed-list")
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun homeFoldGestureKeepsTheListViewportStable() {
        for (grid in booleanArrayOf(true, false)) {
            Hawk.put(HawkConfig.HOME_REC_STYLE, grid)
            ActivityScenario.launch(HomeActivity::class.java).use { home ->
                await(BooleanSupplier { homeShows(home, "手机测试源", "手机播放测试") })
                home.onActivity {
                    val catalog = ArrayList<Movie.Video>()
                    for (i in 0 until 120) {
                        val item = Movie.Video()
                        item.id = "stable$i"
                        item.name = "稳定滚动 $i"
                        catalog.add(item)
                    }
                    UserFragment.homeHotVodAdapter?.setNewData(catalog)
                }
                SystemClock.sleep(700)
                val measuredHeight = intArrayOf(0)
                val resizedFrames = intArrayOf(0)
                val frames = intArrayOf(0)
                val listener = ViewTreeObserver.OnPreDrawListener {
                    val height = UserFragment.tvHotList?.measuredHeight ?: 0
                    if (measuredHeight[0] != 0 && height != measuredHeight[0]) resizedFrames[0]++
                    measuredHeight[0] = height
                    frames[0]++
                    true
                }
                home.onActivity { UserFragment.tvHotList?.viewTreeObserver?.addOnPreDrawListener(listener) }
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true))
                onView(withId(R.id.tvHotList)).perform(swipeVisible(false))
                home.onActivity { UserFragment.tvHotList?.viewTreeObserver?.removeOnPreDrawListener(listener) }
                assertTrue("The gesture must render several frames", frames[0] > 3)
                assertEquals("Folding must not repeatedly resize the list and move touch coordinates", 0, resizedFrames[0])
                screenshot(if (grid) "home-stable-grid" else "home-stable-list")
            }
        }
    }

    private fun swipeVisible(upward: Boolean): ViewAction {
        return swipeVisible(upward, Swipe.SLOW)
    }

    private fun swipeVisible(upward: Boolean, speed: Swipe): ViewAction {
        val start = CoordinatesProvider { view ->
            val r = Rect()
            view.getGlobalVisibleRect(r)
            floatArrayOf(r.centerX().toFloat(), if (upward) r.bottom - r.height() * .1f else r.top + r.height() * .1f)
        }
        val end = CoordinatesProvider { view ->
            val r = Rect()
            view.getGlobalVisibleRect(r)
            floatArrayOf(r.centerX().toFloat(), if (upward) r.top + r.height() * .1f else r.bottom - r.height() * .1f)
        }
        val gesture = GeneralSwipeAction(speed, start, end, Press.FINGER)
        return onVisibleView(gesture)
    }

    private fun onVisibleView(gesture: ViewAction): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isDisplayed()
            }

            override fun getDescription(): String {
                return "swipe within the visible list viewport"
            }

            override fun perform(ui: UiController, view: View) {
                gesture.perform(ui, view)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneSearchHistoryAndHotWordsUseFullScrollableArea() {
        val oldMode = Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)
        val history = ArrayList<String>()
        for (i in 0 until 20) history.add("搜索历史条目 $i")
        history.add("很长的搜索词也应当完整布局而不能撑出手机屏幕边界的测试条目")
        Hawk.put(HawkConfig.SEARCH_HISTORY, history)
        try {
            for (aggregate in booleanArrayOf(true, false)) {
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, aggregate)
                ActivityScenario.launch(SearchActivity::class.java).use { search ->
                    onView(withText("搜索历史条目 0")).check(matches(isDisplayed()))
                    onView(withId(R.id.tvClear)).check(matches(not(isDisplayed())))
                    onView(withId(R.id.etSearch)).perform(replaceText("手机播放测试"), closeSoftKeyboard())
                    onView(withId(R.id.tvClear)).perform(click())
                    onView(withId(R.id.etSearch)).check(matches(withText("")))
                    await(BooleanSupplier {
                        val loaded = booleanArrayOf(false)
                        search.onActivity { activity ->
                            val hot = activity.findViewById<RecyclerView>(R.id.mGridViewWord)
                            loaded[0] = (hot.adapter?.itemCount ?: 0) > 0
                        }
                        loaded[0]
                    })
                    search.onActivity { activity ->
                        val scroll = activity.findViewById<NestedScrollView>(R.id.mobileSearchSuggestions)
                        assertTrue(scroll.height > activity.window.decorView.height / 2)
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                    SystemClock.sleep(400)
                    search.onActivity { activity ->
                        val hot = activity.findViewById<RecyclerView>(R.id.mGridViewWord)
                        val last = hot.findViewHolderForAdapterPosition(hot.adapter!!.itemCount - 1)
                        assertNotNull(last)
                        val visible = Rect()
                        assertTrue(last!!.itemView.getGlobalVisibleRect(visible))
                        assertEquals("Last hot word must be reachable in full", last.itemView.height, visible.height())
                    }
                    screenshot(if (aggregate) "search-scroll-aggregate" else "search-scroll-normal")
                    onView(withId(R.id.mobileSearchSuggestions)).perform(swipeDown())
                    search.onActivity { activity ->
                        activity.findViewById<NestedScrollView>(R.id.mobileSearchSuggestions).scrollTo(0, 0)
                    }
                    onView(withId(R.id.tvHistoryClear)).perform(click())
                    onView(withId(R.id.mobileSearchHistoryEmpty)).check(matches(isDisplayed()))
                    Hawk.put(HawkConfig.SEARCH_HISTORY, history)
                }
            }
        } finally {
            Hawk.put(HawkConfig.FAST_SEARCH_MODE, oldMode)
        }
    }

    @Test
    @Throws(Exception::class)
    fun normalSearchResultsReplaceSuggestionsAndBackRestoresHistory() {
        Hawk.put(HawkConfig.FAST_SEARCH_MODE, false)
        ActivityScenario.launch(SearchActivity::class.java).use { search ->
            onView(withId(R.id.etSearch)).perform(replaceText("手机播放测试"), pressImeActionButton())
            await(BooleanSupplier {
                val loaded = booleanArrayOf(false)
                search.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.mGridView)
                    loaded[0] = list.isShown && (list.adapter?.itemCount ?: 0) > 0
                }
                loaded[0]
            })
            onView(withId(R.id.mobileSearchSuggestions)).check(matches(not(isDisplayed())))
            onView(withId(R.id.mobileSearchResults)).check(matches(isDisplayed()))
            screenshot("search-normal-results")
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.mobileSearchSuggestions)).check(matches(isDisplayed()))
            onView(withId(R.id.mobileSearchResults)).check(matches(not(isDisplayed())))
        }
        Hawk.put(HawkConfig.FAST_SEARCH_MODE, true)
    }

    @Test
    @Throws(Exception::class)
    fun homeLayoutSettingKeepsBothModesVerticallyScrollable() {
        ActivityScenario.launch(HomeActivity::class.java).use { home ->
            await(BooleanSupplier { homeShows(home, "手机测试源", "手机播放测试") })
            for (mode in arrayOf("单列列表", "多列海报")) {
                home.onActivity { activity -> activity.startActivity(Intent(activity, SettingActivity::class.java)) }
                onView(withId(R.id.llHomeRecStyle)).perform(scrollTo(), click())
                onView(withText(mode)).inRoot(isDialog()).perform(click())
                androidx.test.espresso.Espresso.pressBack()
                val catalog = ArrayList<Movie.Video>()
                for (i in 1..20) {
                    val item = Movie.Video()
                    item.id = "list-$i"
                    item.name = "列表影片$i"
                    item.pic = ""
                    item.sourceKey = "phone-fixture"
                    catalog.add(item)
                }
                home.onActivity { activity ->
                    UserFragment.homeHotVodAdapter?.setNewData(catalog)
                    val list = activity.findViewById<RecyclerView>(R.id.tvHotList)
                    val manager = list.layoutManager as GridLayoutManager
                    assertEquals(if ("单列列表" == mode) 1 else 3, manager.spanCount)
                    assertTrue(manager.canScrollVertically())
                    assertFalse(manager.canScrollHorizontally())
                    list.scrollToPosition(19)
                }
                SystemClock.sleep(400)
                onView(withId(R.id.tvHotList)).perform(swipeVisible(true))
                onView(withText("列表影片20")).check(matches(isDisplayed()))
                screenshot(if ("单列列表" == mode) "home-list-mode" else "home-grid-mode")
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneConfigurationPasteKeepsSeparateVodAndLiveAddresses() {
        val saved = AtomicReference<String>()
        val s = server!!
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            settings.onActivity { activity ->
                val dialog = ApiDialog(activity)
                dialog.setOnListener(object : ApiDialog.OnListener {
                    override fun onchange(api: String) {
                        saved.set(api)
                    }

                    override fun onLocalConfig(live: Boolean) {}
                })
                dialog.show()
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("config", s.base() + "/config2"))
            }
            onView(withId(R.id.mobilePasteApi)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.input)).inRoot(isDialog()).check(matches(withText(s.base() + "/config2")))
            settings.onActivity { activity ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("live", s.base() + "/live.txt"))
            }
            onView(withId(R.id.mobilePasteLive)).inRoot(isDialog()).perform(scrollTo(), click())
            onView(withId(R.id.ivQRCode)).inRoot(isDialog()).check(matches(not(isDisplayed())))
            screenshot("configuration-paste")
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).perform(click())
            assertEquals(s.base() + "/config2", saved.get())
            assertEquals(s.base() + "/live.txt", Hawk.get<Any>(HawkConfig.LIVE_API_URL))
        }
    }

    @Test
    @Throws(Exception::class)
    fun linkPlaybackAcceptsPastedPhoneInput() {
        val playbackActivity = AtomicReference<Activity>()
        val s = server!!
        try {
            ActivityScenario.launch(PushActivity::class.java).use { push ->
                push.onActivity { activity ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("video", s.base() + "/video.mp4"))
                }
                onView(withId(R.id.pushLocal)).perform(click())
                onView(withId(R.id.mobileLinkInput)).check(matches(withText(s.base() + "/video.mp4")))
                onView(withId(R.id.mobilePlayLink)).perform(click())
                await(BooleanSupplier {
                    val ready = booleanArrayOf(false)
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        for (activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                            if (activity is DetailActivity) {
                                playbackActivity.set(activity)
                                val player = activity.findViewById<VideoView<*>>(R.id.mVideoView)
                                ready[0] = player != null && player.getDuration() > 0
                            }
                        }
                    }
                    ready[0]
                })
                assertTrue(s.videoRequests > 0)
            }
        } finally {
            if (playbackActivity.get() != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { playbackActivity.get().finish() }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun configurationSheetSupportsPhoneKeyboard() {
        ActivityScenario.launch(SettingActivity::class.java).use {
            onView(withId(R.id.llApi)).perform(click())
            onView(withId(R.id.input)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                assertTrue("Address input is too narrow", view.width >= MobileSheets.dp(context, 180f))
            }.perform(click())
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.input)).inRoot(isDialog()).perform(pressImeActionButton())
            onView(withId(R.id.inputLive)).inRoot(isDialog()).check(matches(hasFocus()))
            screenshot("configuration-keyboard")
            onView(withId(R.id.input)).inRoot(isDialog()).perform(closeSoftKeyboard())
            onView(withId(R.id.inputConfirm)).inRoot(isDialog()).check(matches(isDisplayed()))
            screenshot("configuration-form")
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    @Throws(Exception::class)
    fun localDocumentUriDecodesWithoutSubscriptionParser() {
        val s = server!!
        val video = File(context.cacheDir, "local-smoke-" + s.listeningPort + ".mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("smoke.mp4").use { `in` ->
            FileOutputStream(video).use { `out` ->
                val buffer = ByteArray(8192)
                var read: Int
                while (`in`.read(buffer).also { read = it } != -1) `out`.write(buffer, 0, read)
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", video)
        try {
            ActivityScenario.launch<DetailActivity>(LocalVideos.intent(context, uri)).use { detail ->
                await(BooleanSupplier {
                    val prepared = booleanArrayOf(false)
                    detail.onActivity { activity ->
                        val player = activity.findViewById<VideoView<*>>(R.id.mVideoView)
                        prepared[0] = player != null && player.getDuration() > 0 && player.getCurrentPosition() > 0
                    }
                    prepared[0]
                })
                assertEquals(0, s.videoRequests)
                screenshot("local-video")
            }
        } finally {
            video.delete()
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneSearchUsesSystemInputAndFavoritesRemainAvailable() {
        ActivityScenario.launch(SearchActivity::class.java).use { search ->
            SystemClock.sleep(500)
            search.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.etSearch)
                assertTrue(input.showSoftInputOnFocus)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.keyBoardRoot).visibility)
                input.setText("手机播放测试")
                assertNotNull(activity.findViewById(R.id.tvSearchCheckboxBtn))
            }
            screenshot("search")
        }
        val vod = VodInfo()
        vod.id = "fixture"
        vod.sourceKey = "phone-fixture"
        vod.name = "手机播放测试"
        vod.pic = ""
        RoomDataManger.insertVodCollect(vod.sourceKey, vod)
        try {
            ActivityScenario.launch(CollectActivity::class.java).use { collect ->
                SystemClock.sleep(500)
                collect.onActivity { activity -> assertNotNull(activity.findViewById(R.id.mGridView)) }
                screenshot("favorites")
            }
        } finally {
            RoomDataManger.deleteVodCollect(vod.sourceKey, vod)
        }
    }

    @Test
    @Throws(Exception::class)
    fun aggregatedSearchHonorsListAndPosterPreferences() {
        for (mode in intArrayOf(0, 1)) {
            Hawk.put(HawkConfig.SEARCH_VIEW, mode)
            val intent = Intent(context, FastSearchActivity::class.java).putExtra("title", "手机播放测试")
            ActivityScenario.launch<FastSearchActivity>(intent).use { search ->
                await(BooleanSupplier {
                    val loaded = booleanArrayOf(false)
                    search.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.mGridView)
                        loaded[0] = (list.adapter?.itemCount ?: 0) > 0
                    }
                    loaded[0]
                })
                search.onActivity { activity ->
                    for (id in intArrayOf(R.id.mGridView, R.id.mGridViewFilter)) {
                        val list = activity.findViewById<RecyclerView>(id)
                        val manager = list.layoutManager as GridLayoutManager
                        assertEquals(if (mode == 0) 1 else 3, manager.spanCount)
                        assertTrue(manager.canScrollVertically())
                        assertFalse(manager.canScrollHorizontally())
                    }
                }
                search.onActivity { activity ->
                    val sources = activity.findViewById<RecyclerView>(R.id.mGridViewWord)
                    assertTrue("Source chips must leave room for other sources", sources.getChildAt(0).width < sources.width * .75f)
                    assertNotNull(activity.findViewById(R.id.mobileSearchBack))
                }
                screenshot(if (mode == 0) "search-results-list" else "search-results-posters")
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun nativePlayerExtractsVideoFromHtmlAndDecodesIt() {
        val s = server!!
        val intent = Intent(context, DetailActivity::class.java)
            .putExtra("id", s.itemId())
            .putExtra("sourceKey", "phone-fixture")
            .putExtra("title", "手机播放测试")
        ActivityScenario.launch<DetailActivity>(intent).use { detail ->
            val player = AtomicReference<VideoView<*>>()
            await(BooleanSupplier {
                detail.onActivity { activity -> player.set(activity.findViewById(R.id.mVideoView)) }
                player.get() != null
            })
            await(BooleanSupplier {
                val prepared = booleanArrayOf(false)
                detail.onActivity { prepared[0] = player.get().getDuration() > 0 }
                prepared[0]
            })
            if (!InstrumentationRegistry.getArguments().containsKey("externalPlaybackUrl")) {
                assertTrue("The native parser did not request the video", s.videoRequests > 0)
            }
            screenshot("detail")
            detail.onActivity { activity -> activity.findViewById<View>(R.id.tvPlay).performClick() }
            await(BooleanSupplier {
                val landscape = booleanArrayOf(false)
                detail.onActivity { activity -> landscape[0] = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                landscape[0]
            })
            screenshot("playback")
            onView(withId(R.id.mVideoView)).perform(click())
            screenshot("player-controls")
            onView(withText("更多")).perform(click())
            screenshot("player-more")
            onView(withText("字幕")).inRoot(isDialog()).perform(scrollTo(), click())
            onView(withId(R.id.subtitleSizePlus)).inRoot(isDialog()).check(matches(isDisplayed()))
            screenshot("player-subtitle")
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click())
            onView(withId(R.id.mVideoView)).perform(click())
            SystemClock.sleep(500)
            onView(withText("选集")).perform(click())
            onView(withText("第1集")).inRoot(isDialog()).check(matches(isDisplayed()))
            screenshot("player-episodes")
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click())
            detail.onActivity { activity -> activity.onBackPressed() }
            await(BooleanSupplier {
                val portrait = booleanArrayOf(false)
                detail.onActivity { activity -> portrait[0] = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                portrait[0]
            })
            detail.onActivity { activity -> assertTrue(activity.findViewById<View>(R.id.mGridViewFlag).isShown) }
            screenshot("playback-return")
        }
    }

    @Test
    @Throws(Exception::class)
    fun selectingSourceRefreshesHomeRecommendations() {
        ActivityScenario.launch(HomeActivity::class.java).use { home ->
            await(BooleanSupplier { homeShows(home, "手机测试源", "手机播放测试") })
            home.onActivity { activity -> activity.findViewById<View>(R.id.tvName).performClick() }
            screenshot("source-dialog")
            onView(withText("第二测试源")).inRoot(isDialog()).perform(click())
            await(BooleanSupplier { homeShows(home, "第二测试源", "第二线路内容") })
            screenshot("source-switched")
        }
    }

    @Test
    @Throws(Exception::class)
    fun switchingConfigurationLineAutomaticallyReloadsHome() {
        val s = server!!
        Hawk.put(HawkConfig.API_LINE_SOURCE, s.base() + "/collection")
        Hawk.put(
            HawkConfig.API_LINE_LIST, ArrayList(
                Arrays.asList(
                    HistoryHelper.buildApiLine("第一配置", s.base() + "/config"),
                    HistoryHelper.buildApiLine("第二配置", s.base() + "/config2")
                )
            )
        )
        val newHome = AtomicReference<HomeActivity>()
        try {
            ActivityScenario.launch(SettingActivity::class.java).use { settings ->
                settings.onActivity { activity -> activity.findViewById<View>(R.id.llApiLine).performClick() }
                onView(withText("第二配置")).inRoot(isDialog()).perform(click())
                await(BooleanSupplier {
                    val ready = booleanArrayOf(false)
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        for (activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                            if (activity is HomeActivity) {
                                newHome.set(activity)
                                val adapter = UserFragment.homeHotVodAdapter
                                ready[0] = adapter != null && adapter.data.any { vod -> "第二线路内容" == vod.name }
                            }
                        }
                    }
                    ready[0]
                })
                assertEquals(s.base() + "/config2", Hawk.get<Any>(HawkConfig.API_URL))
                screenshot("config-switched")
            }
        } finally {
            if (newHome.get() != null) InstrumentationRegistry.getInstrumentation().runOnMainSync { newHome.get().finish() }
            HistoryHelper.clearApiLineList()
        }
    }

    @Test
    @Throws(Exception::class)
    fun liveTvLoadsChannelAndDecodesVideo() {
        ActivityScenario.launch(LivePlayActivity::class.java).use { live ->
            await(BooleanSupplier {
                val playing = booleanArrayOf(false)
                live.onActivity { activity ->
                    val video = activity.findViewById<VideoView<*>>(R.id.mVideoView)
                    playing[0] = video != null && video.getDuration() > 0
                }
                playing[0]
            })
            live.onActivity { activity -> assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation) }
            screenshot("live")
            onView(withText("频道")).perform(click())
            screenshot("live-channels")
            onView(withId(R.id.tvChannelName)).check(matches(isDisplayed()))
            onView(withText("设置")).perform(click())
            screenshot("live-settings")
            onView(withId(R.id.tvRightSettingLayout)).check(matches(isDisplayed()))
        }
    }

    private fun homeShows(home: ActivityScenario<HomeActivity>, source: String, title: String): Boolean {
        val matches = booleanArrayOf(false)
        home.onActivity { activity ->
            val label = activity.findViewById<TextView>(R.id.tvName)
            val adapter = UserFragment.homeHotVodAdapter
            matches[0] = source.contentEquals(label.text) && adapter != null && adapter.data.any { vod -> title == vod.name }
        }
        return matches[0]
    }

    @Test
    @Throws(Exception::class)
    fun importsFlutterDataOnceWithoutRemovingOriginals() {
        val legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val migration = context.getSharedPreferences("native_migration", Context.MODE_PRIVATE)
        val media = "[{\"sourceKey\":\"sub::migration-source\",\"id\":\"old-id\",\"detailId\":\"detail-id\",\"title\":\"迁移测试\"}]"
        val saved = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"sub::migration-source::old-id\"]"
        legacy.edit().putString("flutter.source.current", "sub::migration-source")
            .putString("flutter.subscriptions", "[{\"id\":\"sub\",\"url\":\"https://example.test/root\",\"selectedUrl\":\"https://example.test/line\"}]")
            .putString("flutter.library.media", media).putString("flutter.library.history", saved)
            .putString("flutter.library.favorites", saved).commit()
        migration.edit().clear().commit()
        Hawk.delete(HawkConfig.API_URL)
        try {
            LegacyFlutterImport.run(context)
            assertTrue(migration.getBoolean("flutter_imported", false))
            assertEquals("https://example.test/line", Hawk.get<Any>(HawkConfig.API_URL))
            assertEquals("migration-source", Hawk.get<Any>(HawkConfig.HOME_API))
            assertEquals("https://example.test/root", Hawk.get<Any>(HawkConfig.API_LINE_SOURCE))
            assertEquals("迁移测试", RoomDataManger.getVodInfo("migration-source", "detail-id")?.name)
            assertTrue(RoomDataManger.isVodCollect("migration-source", "detail-id"))
            assertEquals(media, legacy.getString("flutter.library.media", ""))
            Hawk.put(HawkConfig.API_URL, "https://example.test/new-selection")
            LegacyFlutterImport.run(context)
            assertEquals("https://example.test/new-selection", Hawk.get<Any>(HawkConfig.API_URL))
        } finally {
            val vod = VodInfo()
            vod.id = "detail-id"
            RoomDataManger.deleteVodRecord("migration-source", vod)
            RoomDataManger.deleteVodCollect("migration-source", vod)
            legacy.edit().clear().commit()
            HistoryHelper.clearApiLineList()
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneDialogsKeepFieldsAndActionsWithinScreen() {
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            val layouts = arrayOf(
                "dialog_backup", "dialog_search_subtitle", "dialog_remote", "dialog_checkbox_search",
                "dialog_search_remotetv", "dialog_quick_search", "dialog_tip", "dialog_episode",
                "dialog_confirm", "dialog_api", "dialog_cast", "dialog_subtitle", "dialog_about",
                "dialog_danmu_api", "dialog_desc", "dialog_api_history", "dialog_danmu_setting",
                "dialog_search_danmu", "dialog_select", "dialog_xwalk", "dialog_live_password",
                "dialog_grid_filter"
            )
            for (layout in layouts) {
                val shown = AtomicReference<BaseDialog>()
                settings.onActivity { activity ->
                    val dialog = BaseDialog(activity)
                    dialog.setContentView(context.resources.getIdentifier(layout, "layout", context.packageName))
                    dialog.show()
                    shown.set(dialog)
                }
                SystemClock.sleep(150)
                settings.onActivity { activity ->
                    val decor = shown.get().window!!.decorView as ViewGroup
                    assertTrue(layout, decor.width <= activity.window.decorView.width)
                    assertContainedHorizontally(decor, layout, false)
                }
                screenshot(layout)
                settings.onActivity { shown.get().dismiss() }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneSheetsKeepLongEpisodeListsAndFiltersUsable() {
        val s = server!!
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            val episodes = ArrayList<VodInfo.VodSeries>()
            for (i in 1..120) episodes.add(VodInfo.VodSeries("第" + i + "集", s.base() + "/video.mp4"))
            val episodeDialog = AtomicReference<EpisodeDialog>()
            val selected = AtomicInteger(-1)
            settings.onActivity { activity ->
                val dialog = EpisodeDialog(activity, "共 120 集", episodes, 119) { selected.set(it) }
                episodeDialog.set(dialog)
                dialog.show()
            }
            SystemClock.sleep(800)
            settings.onActivity { activity ->
                val decor = episodeDialog.get().window!!.decorView
                assertTrue("Episode dialog must leave room above the sheet", decor.height < activity.window.decorView.height)
            }
            onView(withText("第120集")).inRoot(isDialog()).check(matches(isDisplayed()))
            screenshot("episodes-long-list")
            onView(withText("第120集")).inRoot(isDialog()).perform(click())
            assertEquals(119, selected.get())

            val category = MovieSort.SortData("movie", "电影")
            val filter = MovieSort.SortFilter()
            filter.key = "year"
            filter.name = "年份"
            filter.values = LinkedHashMap<String, String>().apply {
                put("2026", "2026")
                put("2025", "2025")
            }
            category.filters.add(filter)
            val changed = booleanArrayOf(false)
            settings.onActivity { activity ->
                val dialog = GridFilterDialog(activity)
                dialog.setData(category)
                dialog.setOnDismiss { changed[0] = true }
                dialog.show()
            }
            onView(withText("2026")).inRoot(isDialog()).perform(click())
            screenshot("filter-selection")
            onView(withContentDescription("关闭弹窗")).inRoot(isDialog()).perform(click())
            assertTrue(changed[0])
            assertEquals("2026", category.filterSelect["year"])
        }
    }

    @Test
    @Throws(Exception::class)
    fun cancelAndSwipeDismissNeverClearFavorites() {
        val s = server!!
        val vod = VodInfo()
        vod.id = s.itemId()
        vod.name = "保留的收藏"
        vod.sourceKey = "phone-fixture"
        RoomDataManger.insertVodCollect(vod.sourceKey, vod)
        try {
            ActivityScenario.launch(CollectActivity::class.java).use { collect ->
                collect.onActivity { activity -> activity.findViewById<View>(R.id.tvClear).performClick() }
                onView(withText("取消")).inRoot(isDialog()).perform(click())
                assertTrue(RoomDataManger.isVodCollect(vod.sourceKey, vod.id))
                collect.onActivity { activity -> activity.findViewById<View>(R.id.tvClear).performClick() }
                screenshot("confirm-clear")
                onView(withContentDescription("下滑关闭弹窗")).inRoot(isDialog()).perform(
                    GeneralSwipeAction(
                        Swipe.SLOW,
                        GeneralLocation.CENTER,
                        { view ->
                            val location = IntArray(2)
                            view.getLocationOnScreen(location)
                            floatArrayOf(location[0] + view.width / 2f, (location[1] + MobileSheets.dp(context, 100f)).toFloat())
                        },
                        Press.FINGER
                    )
                )
                onView(withContentDescription("关闭弹窗")).check(doesNotExist())
                assertTrue(RoomDataManger.isVodCollect(vod.sourceKey, vod.id))
            }
        } finally {
            RoomDataManger.deleteVodCollect(vod.sourceKey, vod)
        }
    }

    private fun assertContainedHorizontally(view: View, layout: String, horizontalScroller: Boolean) {
        val scrolling = horizontalScroller || view is HorizontalScrollView || view is RecyclerView
        val visible = Rect()
        if (!scrolling && view is TextView && view.visibility == View.VISIBLE &&
            view.getGlobalVisibleRect(visible) && visible.height() > 4
        ) {
            assertTrue(
                "$layout: field clipped horizontally: ${view.text}",
                visible.width() + 3 >= view.width
            )
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                assertContainedHorizontally(view.getChildAt(i), layout, scrolling)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun phoneUtilityScreensOpenAndExposeTouchActions() {
        ActivityScenario.launch(PushActivity::class.java).use { push ->
            push.onActivity { activity -> assertTrue(activity.findViewById<View>(R.id.pushLocal).hasOnClickListeners()) }
            screenshot("push")
        }
        ActivityScenario.launch(LocalFileActivity::class.java).use { local ->
            local.onActivity { activity -> assertNotNull(activity.findViewById(R.id.fileList)) }
            screenshot("local-files")
        }
    }

    @Test
    @Throws(Exception::class)
    fun searchNavigationUsesTheInputRowWithoutAnotherTitleBar() {
        ActivityScenario.launch(SearchActivity::class.java).use { search ->
            onView(withId(R.id.mobileSearchBack)).check(matches(isDisplayed()))
            search.onActivity { activity ->
                val back = activity.findViewById<View>(R.id.mobileSearchBack)
                val input = activity.findViewById<View>(R.id.etSearch)
                val backAt = IntArray(2)
                val inputAt = IntArray(2)
                back.getLocationOnScreen(backAt)
                input.getLocationOnScreen(inputAt)
                assertTrue(Math.abs(backAt[1] - inputAt[1]) < MobileSheets.dp(activity, 8f))
                assertTrue("Search input should be at the top of the page", inputAt[1] < MobileSheets.dp(activity, 64f))
            }
            screenshot("audit-search-inline-navigation")
            onView(withId(R.id.mobileSearchBack)).perform(click())
            // Activity teardown completes asynchronously after the click/transition.
            await(BooleanSupplier { search.state == Lifecycle.State.DESTROYED })
        }
    }

    @Test
    @Throws(Exception::class)
    fun populatedDialogListsExposeTheLastRowWithoutClippedFields() {
        val cases = arrayOf(
            intArrayOf(R.layout.dialog_search_subtitle, R.id.mGridView, R.layout.item_search_subtitle_result, R.id.subtitleName),
            intArrayOf(R.layout.dialog_search_danmu, R.id.mGridView, R.layout.item_search_danmu_result, R.id.danmuName),
            intArrayOf(R.layout.dialog_quick_search, R.id.mGridView, R.layout.item_quick_search_lite, R.id.tvName),
            intArrayOf(R.layout.dialog_backup, R.id.list, R.layout.item_dialog_backup, R.id.tvName),
            intArrayOf(R.layout.dialog_select, R.id.list, R.layout.item_dialog_select, R.id.tvName),
            intArrayOf(R.layout.dialog_api_history, R.id.list, R.layout.item_dialog_api_history, R.id.tvName),
            intArrayOf(R.layout.dialog_cast, R.id.list, R.layout.item_cast_device, R.id.tvDeviceName),
            intArrayOf(R.layout.dialog_checkbox_search, R.id.mGridView, R.layout.item_dialog_checkbox_search, R.id.oneSearchSource)
        )
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            for (spec in cases) {
                val shown = AtomicReference<BaseDialog>()
                val name = context.resources.getResourceEntryName(spec[0])
                settings.onActivity { activity ->
                    val dialog = BaseDialog(activity)
                    dialog.setContentView(spec[0])
                    val list = dialog.findViewById<RecyclerView>(spec[1])
                    list.layoutManager = LinearLayoutManager(activity)
                    val data = ArrayList<String>()
                    for (i in 1..40) data.add("第" + i + "项 · 很长的影片及文件名称用于检查手机上的文字边界")
                    list.adapter = object : BaseQuickAdapter<String, BaseViewHolder>(spec[2], data) {
                        override fun convert(holder: BaseViewHolder, value: String) {
                            holder.setText(spec[3], value)
                        }
                    }
                    val spinner = dialog.findViewById<View>(R.id.progressBar)
                    spinner?.visibility = View.GONE
                    val scanning = dialog.findViewById<View>(R.id.tvScanning)
                    scanning?.visibility = View.GONE
                    dialog.show()
                    shown.set(dialog)
                }
                SystemClock.sleep(250)
                settings.onActivity { activity ->
                    val list = shown.get().findViewById<RecyclerView>(spec[1])
                    assertTrue("$name has no useful list viewport", list.height >= MobileSheets.dp(activity, 48f))
                    list.scrollToPosition(39)
                }
                SystemClock.sleep(250)
                settings.onActivity {
                    val list = shown.get().findViewById<RecyclerView>(spec[1])
                    val last = list.findViewHolderForAdapterPosition(39)
                    assertNotNull(name, last)
                    last!!.itemView.requestRectangleOnScreen(Rect(0, 0, last.itemView.width, last.itemView.height), true)
                }
                SystemClock.sleep(200)
                try {
                    settings.onActivity {
                        val list = shown.get().findViewById<RecyclerView>(spec[1])
                        val row = list.findViewHolderForAdapterPosition(39)!!.itemView
                        val visible = Rect()
                        assertTrue(name, row.getGlobalVisibleRect(visible))
                        assertEquals("$name last row is cut off", row.height, visible.height())
                        assertContainedHorizontally(row, name, false)
                    }
                } finally {
                    screenshot("audit-filled-$name")
                    settings.onActivity { shown.get().dismiss() }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun filterOptionsWrapAndRestoreTheSelectionWithoutStaleViews() {
        val category = MovieSort.SortData("movie", "电影")
        val filter = MovieSort.SortFilter()
        filter.key = "area"
        filter.name = "地区"
        filter.values = LinkedHashMap<String, String>().apply {
            for (i in 1..36) put("地区选项$i", "area$i")
        }
        category.filters.add(filter)
        category.filterSelect["area"] = "area1"
        ActivityScenario.launch(SettingActivity::class.java).use { settings ->
            val shown = AtomicReference<GridFilterDialog>()
            settings.onActivity { activity ->
                val dialog = GridFilterDialog(activity)
                dialog.setData(category)
                dialog.show()
                shown.set(dialog)
            }
            onView(withText("地区选项1")).inRoot(isDialog()).check(matches(isSelected())).perform(click())
            assertFalse(category.filterSelect.containsKey("area"))
            settings.onActivity {
                val options = shown.get().findViewById<RecyclerView>(R.id.mFilterKv)
                assertFalse(options.layoutManager!!.canScrollHorizontally())
                val last = options.findViewHolderForAdapterPosition(35)!!.itemView
                last.requestRectangleOnScreen(Rect(0, 0, last.width, last.height), true)
            }
            onView(withText("地区选项36")).inRoot(isDialog()).perform(click())
            assertEquals("area36", category.filterSelect["area"])
            screenshot("audit-filter-options")
            settings.onActivity { shown.get().dismiss() }
        }
    }

    @Test
    @Throws(Exception::class)
    fun detailControlsAndLongEpisodeListsRemainReachableBelowPreview() {
        val s = server!!
        s.episodeCount = 120
        val preview = Hawk.get(HawkConfig.SHOW_PREVIEW, true)
        Hawk.put(HawkConfig.SHOW_PREVIEW, false)
        val intent = Intent(context, DetailActivity::class.java)
            .putExtra("id", s.itemId())
            .putExtra("sourceKey", "phone-fixture")
            .putExtra("title", "手机播放测试")
        try {
            ActivityScenario.launch<DetailActivity>(intent).use { detail ->
                await(BooleanSupplier {
                    val loaded = booleanArrayOf(false)
                    detail.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.mGridView)
                        loaded[0] = list.adapter != null && list.adapter!!.itemCount == 120
                    }
                    loaded[0]
                })
                onView(withId(R.id.tvCollect)).perform(scrollTo()).check(matches(isDisplayed()))
                onView(withId(R.id.mSeriesSortTv)).perform(scrollTo()).check(matches(isDisplayed()))
                detail.onActivity { activity ->
                    val groups = activity.findViewById<RecyclerView>(R.id.mSeriesGroupView)
                    assertTrue("Episode group hit targets must not be clipped", groups.height >= MobileSheets.dp(activity, 48f))
                    val scroll = activity.findViewById<NestedScrollView>(R.id.mobileDetailScroll)
                    scroll.fullScroll(View.FOCUS_DOWN)
                    activity.findViewById<RecyclerView>(R.id.mGridView).scrollToPosition(119)
                }
                SystemClock.sleep(500)
                detail.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.mGridView)
                    val last = list.findViewHolderForAdapterPosition(119)!!.itemView
                    last.requestRectangleOnScreen(Rect(0, 0, last.width, last.height), true)
                }
                onView(withText("第120集")).check(matches(isCompletelyDisplayed()))
                screenshot("audit-detail-long-episodes")
            }
        } finally {
            Hawk.put(HawkConfig.SHOW_PREVIEW, preview)
        }
    }

    @Throws(Exception::class)
    private fun playback(url: String): JSONObject {
        val result = AtomicReference<JSONObject>()
        val latch = CountDownLatch(1)
        val model = SourceViewModel()
        val observer = Observer<JSONObject> { value ->
            result.set(value)
            latch.countDown()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model.playResult.observeForever(observer)
            model.getPlay("phone-fixture", "test", "progress", url, "subtitle")
        }
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            assertNotNull(result.get())
            return result.get()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.playResult.removeObserver(observer) }
        }
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier) {
        val end = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < end) {
            if (condition.asBoolean) return
            SystemClock.sleep(100)
        }
        fail("Native operation did not finish within 30 seconds")
    }

    @Throws(Exception::class)
    private fun screenshot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(600) // Let window and rotation animations reach the captured frame.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(context.cacheDir, "native-$name.png")).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    private class FixtureServer(private val video: ByteArray) : NanoHTTPD("127.0.0.1", 0) {
        @Volatile
        var videoRequests: Int = 0

        @Volatile
        var episodeCount: Int = 1

        private fun episodes(url: String): String {
            val values = ArrayList<String>()
            for (i in 1..episodeCount) values.add("第" + i + "集$" + url)
            return TextUtils.join("#", values)
        }

        fun base(): String {
            return "http://127.0.0.1:$listeningPort"
        }

        fun itemId(): String {
            return "fixture-$listeningPort"
        }

        override fun serve(session: IHTTPSession): Response {
            val b = base()
            val webpage = InstrumentationRegistry.getArguments().getString("externalPlaybackUrl", "$b/watch")
            return when (session.uri) {
                "/config", "/config2" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"sites\":[{\"key\":\"phone-fixture\",\"name\":\"手机测试源\",\"type\":1,\"api\":\"" + b + (if (session.uri == "/config2") "/second" else "/api") + "\"},{\"key\":\"phone-second\",\"name\":\"第二测试源\",\"type\":1,\"api\":\"" + b + "/second\"}],\"lives\":[{\"name\":\"手机直播\",\"type\":0,\"url\":\"" + b + "/live.txt\"}]}"
                )
                "/live.txt" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/plain",
                    "测试分组,#genre#\n测试频道,$b/video.mp4"
                )
                "/watch" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html",
                    "<!doctype html><html><body><video autoplay muted playsinline src='$b/video.mp4'></video></body></html>"
                )
                "/video.mp4" -> {
                    videoRequests++
                    val range = session.headers["range"]
                    var start = 0
                    if (range != null && range.startsWith("bytes=")) {
                        start = range.substring(6).split("-")[0].toInt()
                    }
                    start = Math.min(start, video.size - 1)
                    val response = newFixedLengthResponse(
                        if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT,
                        "video/mp4",
                        ByteArrayInputStream(video, start, video.size - start),
                        (video.size - start).toLong()
                    )
                    response.addHeader("Accept-Ranges", "bytes")
                    if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + (video.size - 1) + "/" + video.size)
                    response
                }
                else -> newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"class\":[{\"type_id\":\"1\",\"type_name\":\"电影\"}],\"page\":1,\"pagecount\":1,\"list\":[{\"vod_id\":\"" + itemId() + "\",\"vod_name\":\"" + (if (session.uri == "/second") "第二线路内容" else "手机播放测试") + "\",\"vod_pic\":\"\",\"vod_play_from\":\"web\" + \"$$$\" + \"direct\",\"vod_play_url\":\"" + episodes(webpage) + "$$$" + episodes("$b/video.mp4") + "\"}]}"
                )
            }
        }
    }
}
