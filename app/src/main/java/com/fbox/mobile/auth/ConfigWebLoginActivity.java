package com.fbox.mobile.auth;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fbox.mobile.MobileSheets;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.greenrobot.eventbus.EventBus;

/** Official HTTPS login pages, with an explicit user action to save the resulting login cookie. */
public final class ConfigWebLoginActivity extends AppCompatActivity {
    private WebView web;
    private TextView status, save;
    private ConfigAuthProvider provider;
    private PluginAuthClient backend;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean saving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        provider = ConfigAuthProvider.byId(getIntent().getStringExtra("provider"));
        SourceBean source = ApiConfig.get().getSource(getIntent().getStringExtra("source"));
        if (provider == null || !provider.supportsWebLogin() || !ConfigAuthProvider.supportsMobileApi(source)) { finish(); return; }
        backend = new PluginAuthClient(source);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(0xFF121927); getWindow().setNavigationBarColor(0xFF121927);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xFF121927);
        LinearLayout header = new LinearLayout(this);
        TextView back = MobileSheets.action(this, "返回"); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView title = MobileSheets.action(this, provider.label + "网页登录");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(header);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(13);
        int padding = MobileSheets.dp(this, 12); status.setPadding(padding, padding, padding, padding);
        status.setText("请在官方页面登录，再点击底部按钮保存到当前订阅。"); body.addView(status);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Captcha services can use HTTPS frames outside the provider's main-page domain.
                if (!request.isForMainFrame()) {
                    String scheme = request.getUrl().getScheme();
                    return !("https".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme));
                }
                return !provider.allows(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !provider.allows(url); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) status.setText("登录页面加载失败，请返回后重试或选择其他登录方式。");
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (provider.allows(url) && !saving) status.setText("官方页面 · " + Uri.parse(url).getHost()
                        + "\n完成登录后，点击下方按钮将登录信息保存到当前订阅。");
            }
        });
        body.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        save = MobileSheets.action(this, "已完成登录，保存到当前订阅");
        save.setOnClickListener(v -> saveLogin()); body.addView(save);
        setContentView(body);
        web.loadUrl(provider.loginUrl);
    }

    private void saveLogin() {
        if (saving) return;
        String cookies = CookieManager.getInstance().getCookie(provider.cookieUrl);
        if (!provider.hasLoginCookie(cookies)) {
            status.setText("尚未检测到有效登录信息，请先完成网页登录；也可以返回使用 App 授权或粘贴凭据。"); return;
        }
        if (cookies.length() > 32768) { status.setText("登录信息过长，请返回使用其他登录方式"); return; }
        saving = true; save.setEnabled(false); status.setText("正在保存登录信息…");
        worker.execute(() -> {
            boolean success = false;
            try { success = backend.request(provider.saveAction, Collections.singletonMap("value", cookies)).optInt("code", -1) == 0; }
            catch (Exception ignored) { }
            boolean saved = success;
            runOnUiThread(() -> {
                if (saved) {
                    SourceViewModel.clearRuntimeCache();
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CONFIG_AUTH_CHANGED));
                }
                if (isFinishing() || isDestroyed()) return;
                saving = false; save.setEnabled(true);
                if (saved) {
                    Toast.makeText(this, "登录信息已保存", Toast.LENGTH_SHORT).show(); finish();
                } else status.setText("保存失败，请检查当前订阅后重试");
            });
        });
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.setWebViewClient(new WebViewClient()); web.destroy(); web = null; }
        worker.shutdown();
        super.onDestroy();
    }
}
