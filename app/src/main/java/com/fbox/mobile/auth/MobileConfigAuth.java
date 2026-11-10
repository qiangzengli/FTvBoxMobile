package com.fbox.mobile.auth;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import com.fbox.mobile.MobileSheets;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.dialog.BaseDialog;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import java.util.Collections;
import java.util.concurrent.Executors;

/** Adapts configuration action cards without executing the plugin's TV-only QR dialogs. */
public final class MobileConfigAuth {
    private MobileConfigAuth() { }

    public static String title(Movie.Video video) {
        SourceBean source = ApiConfig.get().getSource(video.sourceKey);
        if (!ConfigAuthProvider.isQrAction(source, video.action, video.name)) return video.name;
        if (video.name.endsWith("注册")) return video.name;
        return video.name.replace("扫码登录", "手机登录").replace("扫码", "手机授权").replace("二维码", "授权链接");
    }

    public static String note(Movie.Video video) {
        SourceBean source = ApiConfig.get().getSource(video.sourceKey);
        if (!ConfigAuthProvider.isQrAction(source, video.action, video.name)) return video.note;
        if (video.name.endsWith("注册")) return "点击打开注册页面";
        return video.note == null ? "App 授权 / 手机登录" : video.note.replace("未扫码", "未登录").replace("扫码", "授权");
    }

    public static boolean handle(Fragment owner, Movie.Video video, Runnable refreshed) {
        SourceBean source = ApiConfig.get().getSource(video.sourceKey);
        if (!ConfigAuthProvider.isQrAction(source, video.action, video.name)) return false;
        ConfigAuthProvider provider = ConfigAuthProvider.fromTitle(video.name);
        Context context = owner.requireContext();
        if (provider == null || !ConfigAuthProvider.supportsMobileApi(source)) {
            BaseDialog dialog = new BaseDialog(context);
            dialog.setTitle("手机授权暂不可用");
            TextView message = message(context, "这条订阅没有提供可兼容的手机授权接口。请使用配置中心的账号、验证码或手动输入入口，或更换支持手机授权的订阅。");
            dialog.setContentView(message);
            bind(owner, dialog, () -> { });
            dialog.show();
            return true;
        }
        new LoginSheet(owner, source, provider, video.name.endsWith("注册"), refreshed, new PluginAuthClient(source)).show();
        return true;
    }

    private static TextView message(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(15); view.setTextColor(Color.WHITE);
        view.setPadding(0, MobileSheets.dp(context, 12), 0, MobileSheets.dp(context, 16));
        return view;
    }

    private static void bind(Fragment owner, BaseDialog dialog, Runnable cleanup) {
        Lifecycle lifecycle = owner.getViewLifecycleOwner().getLifecycle();
        LifecycleEventObserver observer = (source, event) -> { if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss(); };
        lifecycle.addObserver(observer);
        dialog.setOnDismissListener(d -> { lifecycle.removeObserver(observer); cleanup.run(); });
    }

    static final class LoginSheet {
        private final Fragment owner;
        private final SourceBean source;
        private final ConfigAuthProvider provider;
        private final boolean registration;
        private final Runnable refreshed;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final BaseDialog dialog;
        private final TextView status, appButton, browserButton;
        private final AuthSession.Backend backend;
        private AuthSession session;
        private String url = "";

        LoginSheet(Fragment owner, SourceBean source, ConfigAuthProvider provider, boolean registration, Runnable refreshed, AuthSession.Backend backend) {
            this.owner = owner; this.source = source; this.provider = provider;
            this.registration = registration; this.refreshed = refreshed;
            Context context = owner.requireContext();
            this.backend = backend;
            dialog = new BaseDialog(context);
            dialog.setTitle(provider.label + (registration ? "注册" : "手机登录"));
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            status = message(context, registration ? "正在读取注册入口…" : "正在准备授权…");
            body.addView(status);
            appButton = add(body, "重新尝试 App 授权", this::start);
            browserButton = add(body, "在浏览器继续授权", () -> {
                if (!AuthLinks.openWeb(context, provider, url)) status.setText("没有可用的浏览器，请使用下方登录方式");
                else status.setText("请在打开的页面确认授权，完成后返回 FBox");
            });
            browserButton.setVisibility(View.GONE);
            if (!registration && provider.supportsWebLogin()) add(body, "网页登录", () -> {
                stopSession();
                Intent intent = new Intent(context, ConfigWebLoginActivity.class);
                intent.putExtra("source", source.getKey()); intent.putExtra("provider", provider.id);
                owner.startActivity(intent);
                dialog.dismiss();
            });
            if (!registration && !provider.saveAction.isEmpty()) {
                add(body, provider.cookieUrl.isEmpty() ? "粘贴 Token" : "粘贴 Cookie", () -> {
                    stopSession();
                    showManual();
                });
            }
            if (!registration) body.addView(message(context, "优先打开对应 App。若 App 不支持此授权链接，可选择其他登录方式。只有登录信息保存成功后才会更新状态。"));
            dialog.setContentView(body);
            bind(owner, dialog, this::stopSession);
        }

        private TextView add(LinearLayout body, String label, Runnable click) {
            TextView button = MobileSheets.action(body.getContext(), label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = MobileSheets.dp(body.getContext(), 8);
            body.addView(button, params); button.setOnClickListener(v -> click.run()); return button;
        }

        void show() { dialog.show(); start(); }

        private void stopSession() { if (session != null) { session.close(); session = null; } }

        private void start() {
            stopSession();
            url = "";
            browserButton.setVisibility(View.GONE); appButton.setEnabled(false);
            status.setText(registration ? "正在读取注册入口…" : "正在准备授权…");
            if (registration) {
                java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
                worker.execute(() -> {
                    String link = provider.loginUrl;
                    try {
                        String value = backend.request("status", Collections.emptyMap()).optString(provider.registerKey);
                        if (provider.allows(value)) link = value;
                    } catch (Exception ignored) { }
                    final String target = link;
                    main.post(() -> { if (dialog.isShowing()) {
                        appButton.setEnabled(true);
                        if (AuthLinks.openApp(owner.requireContext(), provider, target)
                                || AuthLinks.openWeb(owner.requireContext(), provider, target)) dialog.dismiss();
                        else status.setText("当前注册入口不可用，请稍后重试");
                    }});
                });
                worker.shutdown();
                return;
            }
            session = new AuthSession(backend, provider.id, Executors.newSingleThreadScheduledExecutor(), main::post, new AuthSession.Listener() {
                @Override public void ready(String payload) {
                    url = AuthLinks.decode(provider, payload);
                    appButton.setEnabled(true);
                    if (url.isEmpty()) {
                        status.setText("此平台未提供可直接打开的授权链接，请使用下方登录方式");
                        stopSession(); return;
                    }
                    browserButton.setVisibility(View.VISIBLE);
                    boolean opened = AuthLinks.openApp(owner.requireContext(), provider, url);
                    status.setText(opened ? "请在打开的 App 中确认授权，完成后返回 FBox"
                            : "没有找到可接收此授权链接的 App，请选择下方其他登录方式。");
                }
                @Override public void waiting() { /* Keep actionable fallback text visible. */ }
                @Override public void success() { saved(); }
                @Override public void failed(String message) {
                    appButton.setEnabled(true); browserButton.setVisibility(View.GONE); status.setText(message);
                }
            });
            session.start();
        }

        private void showManual() {
            Context context = owner.requireContext();
            BaseDialog inputDialog = new BaseDialog(context);
            inputDialog.setTitle(provider.label + " · 粘贴登录信息");
            LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
            EditText input = new EditText(context);
            input.setTextColor(Color.WHITE); input.setHintTextColor(0xFFADB4C4);
            input.setHint(provider.cookieUrl.isEmpty() ? "粘贴 Token" : "粘贴 Cookie");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setMinLines(3); input.setMaxLines(6); input.setTextSize(14);
            body.addView(input);
            TextView result = message(context, "登录信息仅交给当前订阅保存，用于访问你的网盘或账号。"); body.addView(result);
            TextView save = add(body, "保存登录信息", () -> { });
            save.setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) { input.setError("请先粘贴登录信息"); return; }
                if (value.length() > 32768) { input.setError("登录信息过长，请检查内容"); return; }
                save.setEnabled(false); result.setText("正在保存…"); MobileSheets.hideKeyboard(input);
                java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
                worker.execute(() -> {
                    boolean success = false;
                    try { success = backend.request(provider.saveAction, Collections.singletonMap("value", value)).optInt("code", -1) == 0; }
                    catch (Exception ignored) { }
                    boolean saved = success;
                    main.post(() -> {
                        if (!inputDialog.isShowing()) {
                            if (saved) {
                                SourceViewModel.clearRuntimeCache();
                                org.greenrobot.eventbus.EventBus.getDefault().post(new com.github.tvbox.osc.event.RefreshEvent(
                                        com.github.tvbox.osc.event.RefreshEvent.TYPE_CONFIG_AUTH_CHANGED));
                            }
                            return;
                        }
                        save.setEnabled(true);
                        if (saved) { input.setText(""); inputDialog.dismiss(); saved(); }
                        else result.setText("保存失败，请检查登录信息或重新登录");
                    });
                }); worker.shutdown();
            });
            inputDialog.setContentView(body);
            bind(owner, inputDialog, () -> input.setText(""));
            inputDialog.show();
        }

        private void saved() {
            dialog.dismiss();
            SourceViewModel.clearRuntimeCache();
            Toast.makeText(owner.requireContext(), "登录信息已保存", Toast.LENGTH_SHORT).show();
            refreshed.run();
        }
    }
}
