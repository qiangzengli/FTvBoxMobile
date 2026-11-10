package com.fbox.mobile

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.tvbox.osc.R
import androidx.compose.ui.text.style.TextOverflow
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.ui.activity.*

/** Compose navigation with a synchronously attached host for native Fragment screens. */
class MobileFrame(private val activity: BaseActivity, private val content: View) {
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF10131A.toInt())
    }
    private val chrome = ComposeView(activity)
    private val inlineNavigation = activity is SearchActivity || activity is FastSearchActivity

    init {
        val home = activity is HomeActivity
        // FragmentManager must find its containers before Activity.onStart.
        if (!home && !inlineNavigation) view.addView(chrome)
        view.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        if (home) view.addView(chrome)
        setFullscreen(activity is LivePlayActivity)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun attach() {
        if (inlineNavigation) {
            content.findViewById<View>(R.id.mobileSearchBack)?.setOnClickListener { activity.onBackPressed() }
            return
        }
        chrome.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        chrome.setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFAEC6FF),
                background = Color(0xFF10131A),
                surface = Color(0xFF171C26),
            )) {
                if (activity is HomeActivity) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    NavigationBarItem(true, {}, { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
                    NavigationBarItem(false, { open(SearchActivity::class.java) }, { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
                    NavigationBarItem(false, { open(LivePlayActivity::class.java) }, { Icon(Icons.Default.LiveTv, null) }, label = { Text("直播") })
                    NavigationBarItem(false, { open(SettingActivity::class.java) }, { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
                } else TopAppBar(
                    title = { Text(screenTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (activity is HistoryActivity || activity is CollectActivity) {
                            TextButton(onClick = { content.findViewById<View>(R.id.tvDelete)?.performClick() }) { Text("管理") }
                            TextButton(onClick = { content.findViewById<View>(R.id.tvClear)?.performClick() }) { Text("清空") }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = { activity.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                )
            }
        }
    }

    fun setFullscreen(value: Boolean) { chrome.visibility = if (value) View.GONE else View.VISIBLE }
    private fun open(type: Class<out BaseActivity>) = activity.startActivity(Intent(activity, type))
    private fun screenTitle() = when (activity) {
        is DetailActivity -> activity.intent.getStringExtra("title")?.takeIf { it.isNotBlank() } ?: "影片详情"
        is SearchActivity, is FastSearchActivity -> "搜索"
        is SettingActivity -> "设置"
        is HistoryActivity -> "观看历史"
        is CollectActivity -> "收藏"
        is LocalFileActivity -> "本地文件"
        is PushActivity -> "链接播放"
        else -> "FBox"
    }
}
