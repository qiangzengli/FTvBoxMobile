package com.fbox.mobile.auth

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fbox.mobile.MobileSheets
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.viewmodel.SourceViewModel
import org.greenrobot.eventbus.EventBus
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ConfigWebLoginActivity : AppCompatActivity() {
    private var web: WebView? = null
    private var status: TextView? = null
    private var save: TextView? = null
    private var provider: ConfigAuthProvider? = null
    private var backend: PluginAuthClient? = null
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var saving = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prov = ConfigAuthProvider.byId(intent.getStringExtra("provider"))
        val source: SourceBean? = ApiConfig.get().getSource(intent.getStringExtra("source"))
        if (prov == null || !prov.supportsWebLogin() || !ConfigAuthProvider.supportsMobileApi(source)) {
            finish()
            return
        }
        provider = prov
        backend = PluginAuthClient(source!!)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = 0xFF121927.toInt()
        window.navigationBarColor = 0xFF121927.toInt()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setBackgroundColor(0xFF121927.toInt())

        val header = LinearLayout(this)
        val back = MobileSheets.action(this, "返回")
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(-2, -2))

        val title = MobileSheets.action(this, prov.label + "网页登录")
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(header)

        val statusView = TextView(this)
        statusView.setTextColor(Color.WHITE)
        statusView.textSize = 13f
        val padding = MobileSheets.dp(this, 12f)
        statusView.setPadding(padding, padding, padding, padding)
        statusView.text = "请在官方页面登录，再点击底部按钮保存到当前订阅。"
        status = statusView
        body.addView(statusView)

        val webView = WebView(this)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) {
                    val scheme = request.url.scheme
                    return !("https".equals(scheme, ignoreCase = true) || "about".equals(scheme, ignoreCase = true))
                }
                return !(provider?.allows(request.url.toString()) ?: false)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !(provider?.allows(url) ?: false)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    status?.text = "登录页面加载失败，请返回后重试或选择其他登录方式。"
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (provider?.allows(url) == true && !saving) {
                    status?.text = "官方页面 · " + Uri.parse(url).host + "\n完成登录后，点击下方按钮将登录信息保存到当前订阅。"
                }
            }
        }
        body.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        web = webView

        val saveView = MobileSheets.action(this, "已完成登录，保存到当前订阅")
        saveView.setOnClickListener { saveLogin() }
        body.addView(saveView)
        save = saveView

        setContentView(body)
        webView.loadUrl(prov.loginUrl)
    }

    private fun saveLogin() {
        if (saving) return
        val currentProvider = provider ?: return
        val cookies = CookieManager.getInstance().getCookie(currentProvider.cookieUrl)
        if (!currentProvider.hasLoginCookie(cookies)) {
            status?.text = "尚未检测到有效登录信息，请先完成网页登录；也可以返回使用 App 授权或粘贴凭据。"
            return
        }
        if (cookies.length > 32768) {
            status?.text = "登录信息过长，请返回使用其他登录方式"
            return
        }
        saving = true
        save?.isEnabled = false
        status?.text = "正在保存登录信息…"
        worker.execute {
            var success = false
            try {
                success = backend?.request(currentProvider.saveAction, Collections.singletonMap("value", cookies))
                    ?.optInt("code", -1) == 0
            } catch (ignored: Exception) {
            }
            val saved = success
            runOnUiThread {
                if (saved) {
                    SourceViewModel.clearRuntimeCache()
                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_CONFIG_AUTH_CHANGED))
                }
                if (isFinishing || isDestroyed) return@runOnUiThread
                saving = false
                save?.isEnabled = true
                if (saved) {
                    Toast.makeText(this, "登录信息已保存", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    status?.text = "保存失败，请检查当前订阅后重试"
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web != null && web!!.canGoBack()) {
            web!!.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        web?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
            it.destroy()
            web = null
        }
        worker.shutdown()
        super.onDestroy()
    }
}
