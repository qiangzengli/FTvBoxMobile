package com.fbox.mobile.auth

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fbox.mobile.MobileSheets
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.dialog.BaseDialog
import com.github.tvbox.osc.viewmodel.SourceViewModel
import org.greenrobot.eventbus.EventBus
import java.util.Collections
import java.util.concurrent.Executors

object MobileConfigAuth {

    @JvmStatic
    fun title(video: Movie.Video): String {
        val safeName = video.name ?: ""
        val source: SourceBean? = ApiConfig.get().getSource(video.sourceKey)
        if (!ConfigAuthProvider.isQrAction(source, video.action, safeName)) return safeName
        if (safeName.endsWith("注册")) return safeName
        return safeName.replace("扫码登录", "手机登录").replace("扫码", "手机授权").replace("二维码", "授权链接")
    }

    @JvmStatic
    fun note(video: Movie.Video): String? {
        val safeName = video.name ?: ""
        val source: SourceBean? = ApiConfig.get().getSource(video.sourceKey)
        if (!ConfigAuthProvider.isQrAction(source, video.action, safeName)) return video.note
        if (safeName.endsWith("注册")) return "点击打开注册页面"
        val currentNote = video.note
        return currentNote?.replace("未扫码", "未登录")?.replace("扫码", "授权") ?: "App 授权 / 手机登录"
    }

    @JvmStatic
    fun handle(owner: Fragment, video: Movie.Video, refreshed: Runnable): Boolean {
        val safeName = video.name ?: ""
        val source: SourceBean? = ApiConfig.get().getSource(video.sourceKey)
        if (!ConfigAuthProvider.isQrAction(source, video.action, safeName)) return false
        val provider = ConfigAuthProvider.fromTitle(safeName)
        val context = owner.requireContext()
        if (provider == null || !ConfigAuthProvider.supportsMobileApi(source)) {
            val dialog = BaseDialog(context)
            dialog.setTitle("手机授权暂不可用")
            val messageView = message(context, "这条订阅没有提供可兼容的手机授权接口。请使用配置中心的账号、验证码或手动输入入口，或更换支持手机授权的订阅。")
            dialog.setContentView(messageView)
            bind(owner, dialog) {}
            dialog.show()
            return true
        }
        LoginSheet(owner, source!!, provider, safeName.endsWith("注册"), refreshed, PluginAuthClient(source)).show()
        return true
    }

    private fun message(context: Context, text: String): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 15f
        view.setTextColor(Color.WHITE)
        view.setPadding(0, MobileSheets.dp(context, 12f), 0, MobileSheets.dp(context, 16f))
        return view
    }

    private fun bind(owner: Fragment, dialog: BaseDialog, cleanup: Runnable) {
        val lifecycle = owner.viewLifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            lifecycle.removeObserver(observer)
            cleanup.run()
        }
    }

    internal class LoginSheet(
        private val owner: Fragment,
        private val source: SourceBean,
        private val provider: ConfigAuthProvider,
        private val registration: Boolean,
        private val refreshed: Runnable,
        private val backend: AuthSession.Backend
    ) {
        private val main = Handler(Looper.getMainLooper())
        private val dialog: BaseDialog
        private val status: TextView
        private val appButton: TextView
        private val browserButton: TextView
        private var session: AuthSession? = null
        private var url = ""

        init {
            val context = owner.requireContext()
            dialog = BaseDialog(context)
            dialog.setTitle(provider.label + (if (registration) "注册" else "手机登录"))
            val body = LinearLayout(context)
            body.orientation = LinearLayout.VERTICAL
            status = message(context, if (registration) "正在读取注册入口…" else "正在准备授权…")
            body.addView(status)
            appButton = add(body, "重新尝试 App 授权") { start() }
            browserButton = add(body, "在浏览器继续授权") {
                if (!AuthLinks.openWeb(context, provider, url)) {
                    status.text = "没有可用的浏览器，请使用下方登录方式"
                } else {
                    status.text = "请在打开的页面确认授权，完成后返回 FBox"
                }
            }
            browserButton.visibility = View.GONE
            if (!registration && provider.supportsWebLogin()) {
                add(body, "网页登录") {
                    stopSession()
                    val intent = Intent(context, ConfigWebLoginActivity::class.java)
                    intent.putExtra("source", source.key)
                    intent.putExtra("provider", provider.id)
                    owner.startActivity(intent)
                    dialog.dismiss()
                }
            }
            if (!registration && provider.saveAction.isNotEmpty()) {
                add(body, if (provider.cookieUrl.isEmpty()) "粘贴 Token" else "粘贴 Cookie") {
                    stopSession()
                    showManual()
                }
            }
            if (!registration) {
                body.addView(message(context, "优先打开对应 App。若 App 不支持此授权链接，可选择其他登录方式。只有登录信息保存成功后才会更新状态。"))
            }
            dialog.setContentView(body)
            bind(owner, dialog) { stopSession() }
        }

        private fun add(body: LinearLayout, label: String, click: Runnable): TextView {
            val button = MobileSheets.action(body.context, label)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.topMargin = MobileSheets.dp(body.context, 8f)
            body.addView(button, params)
            button.setOnClickListener { click.run() }
            return button
        }

        fun show() {
            dialog.show()
            start()
        }

        private fun stopSession() {
            session?.close()
            session = null
        }

        private fun start() {
            stopSession()
            url = ""
            browserButton.visibility = View.GONE
            appButton.isEnabled = false
            status.text = if (registration) "正在读取注册入口…" else "正在准备授权…"
            if (registration) {
                val worker = Executors.newSingleThreadExecutor()
                worker.execute {
                    var link = provider.loginUrl
                    try {
                        val value = backend.request("status", Collections.emptyMap()).optString(provider.registerKey)
                        if (provider.allows(value)) link = value
                    } catch (ignored: Exception) {
                    }
                    val target = link
                    main.post {
                        if (dialog.isShowing) {
                            appButton.isEnabled = true
                            if (AuthLinks.openApp(owner.requireContext(), provider, target) ||
                                AuthLinks.openWeb(owner.requireContext(), provider, target)
                            ) {
                                dialog.dismiss()
                            } else {
                                status.text = "当前注册入口不可用，请稍后重试"
                            }
                        }
                    }
                }
                worker.shutdown()
                return
            }
            session = AuthSession(
                backend,
                provider.id,
                Executors.newSingleThreadScheduledExecutor(),
                { command -> main.post(command) },
                object : AuthSession.Listener {
                    override fun ready(payload: String) {
                        url = AuthLinks.decode(provider, payload)
                        appButton.isEnabled = true
                        if (url.isEmpty()) {
                            status.text = "此平台未提供可直接打开的授权链接，请使用下方登录方式"
                            stopSession()
                            return
                        }
                        browserButton.visibility = View.VISIBLE
                        val opened = AuthLinks.openApp(owner.requireContext(), provider, url)
                        status.text = if (opened) {
                            "请在打开的 App 中确认授权，完成后返回 FBox"
                        } else {
                            "没有找到可接收此授权链接的 App，请选择下方其他登录方式。"
                        }
                    }

                    override fun waiting() {}

                    override fun success() {
                        saved()
                    }

                    override fun failed(message: String) {
                        appButton.isEnabled = true
                        browserButton.visibility = View.GONE
                        status.text = message
                    }
                }
            )
            session?.start()
        }

        private fun showManual() {
            val context = owner.requireContext()
            val inputDialog = BaseDialog(context)
            inputDialog.setTitle(provider.label + " · 粘贴登录信息")
            val body = LinearLayout(context)
            body.orientation = LinearLayout.VERTICAL
            val input = EditText(context)
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(0xFFADB4C4.toInt())
            input.hint = if (provider.cookieUrl.isEmpty()) "粘贴 Token" else "粘贴 Cookie"
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            input.minLines = 3
            input.maxLines = 6
            input.textSize = 14f
            body.addView(input)
            val result = message(context, "登录信息仅交给当前订阅保存，用于访问你的网盘或账号。")
            body.addView(result)
            val save = add(body, "保存登录信息") {}
            save.setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    input.error = "请先粘贴登录信息"
                    return@setOnClickListener
                }
                if (value.length > 32768) {
                    input.error = "登录信息过长，请检查内容"
                    return@setOnClickListener
                }
                save.isEnabled = false
                result.text = "正在保存…"
                MobileSheets.hideKeyboard(input)
                val worker = Executors.newSingleThreadExecutor()
                worker.execute {
                    var success = false
                    try {
                        success = backend.request(provider.saveAction, Collections.singletonMap("value", value))
                            .optInt("code", -1) == 0
                    } catch (ignored: Exception) {
                    }
                    val saved = success
                    main.post {
                        if (!inputDialog.isShowing) {
                            if (saved) {
                                SourceViewModel.clearRuntimeCache()
                                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_CONFIG_AUTH_CHANGED))
                            }
                            return@post
                        }
                        save.isEnabled = true
                        if (saved) {
                            input.setText("")
                            inputDialog.dismiss()
                            saved()
                        } else {
                            result.text = "保存失败，请检查登录信息或重新登录"
                        }
                    }
                }
                worker.shutdown()
            }
            inputDialog.setContentView(body)
            bind(owner, inputDialog) { input.setText("") }
            inputDialog.show()
        }

        private fun saved() {
            dialog.dismiss()
            SourceViewModel.clearRuntimeCache()
            Toast.makeText(owner.requireContext(), "登录信息已保存", Toast.LENGTH_SHORT).show()
            refreshed.run()
        }
    }
}
