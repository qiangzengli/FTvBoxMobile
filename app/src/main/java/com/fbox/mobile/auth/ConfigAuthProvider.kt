package com.fbox.mobile.auth

import com.github.tvbox.osc.bean.SourceBean
import java.net.URI
import java.util.Locale

enum class ConfigAuthProvider(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val saveAction: String,
    @JvmField val registerKey: String,
    @JvmField val loginUrl: String,
    @JvmField val cookieUrl: String,
    @JvmField val cookieNames: Array<String>,
    @JvmField val domains: Array<String>,
    vararg packages: String
) {
    QUARK("quark", "夸克", "setQuarkCookie", "qkRegister", "https://pan.quark.cn/", "https://pan.quark.cn/", arrayOf("__puus"), arrayOf("quark.cn"), "com.quark.browser"),
    UC("ucCookie", "UC", "setUcCookie", "ucRegister", "https://drive.uc.cn/", "https://drive.uc.cn/", arrayOf("__puus"), arrayOf("uc.cn"), "com.UCMobile"),
    UC_TOKEN("ucToken", "UC TV Token", "setUcToken", "", "", "", arrayOf(), arrayOf("uc.cn"), "com.UCMobile"),
    BAIDU("baidu", "百度网盘", "setBaiduCookie", "baiduRegister", "https://pan.baidu.com/wap/home", "https://pan.baidu.com/", arrayOf("BDUSS"), arrayOf("baidu.com"), "com.baidu.netdisk"),
    BILI("bili", "哔哩哔哩", "setBiliCookie", "", "https://passport.bilibili.com/h5-app/passport/login", "https://www.bilibili.com/", arrayOf("SESSDATA"), arrayOf("bilibili.com"), "tv.danmaku.bili", "com.bilibili.app.in"),
    PAN115("pan115", "115网盘", "setPan115Cookie", "", "https://passport.115.com/", "https://115.com/", arrayOf("UID", "CID", "SEID"), arrayOf("115.com"), "com.ylmf.androidclient"),
    PAN189("pan189", "天翼云盘", "setPan189Cookie", "", "https://cloud.189.cn/", "https://cloud.189.cn/", arrayOf("COOKIE_LOGIN_USER"), arrayOf("189.cn"), "com.cn21.ecloud"),
    GUANGYA("guangya", "光鸭网盘", "setGuangYaToken", "gyRegister", "", "", arrayOf(), arrayOf("guangyapan.com")),
    THUNDER("thunder", "迅雷", "", "xlRegister", "https://pan.xunlei.com/", "", arrayOf(), arrayOf("xunlei.com"), "com.xunlei.downloadprovider"),
    PAN139("pan139", "移动云盘", "", "ydRegister", "https://yun.139.com/", "", arrayOf(), arrayOf("139.com"), "com.chinamobile.mcloud");

    @JvmField
    val packages: Array<out String> = packages

    fun supportsWebLogin(): Boolean {
        return this == BAIDU || this == BILI || this == PAN115 || this == PAN189
    }

    fun allows(value: String?): Boolean {
        if (value == null || value.length > 16384) return false
        try {
            val uri = URI(value)
            if (!"https".equals(uri.scheme, ignoreCase = true) || uri.rawUserInfo != null
                || (uri.port != -1 && uri.port != 443)
            ) return false
            var host = uri.host ?: return false
            host = host.lowercase(Locale.ROOT)
            for (domain in domains) {
                if (host == domain || host.endsWith(".$domain")) return true
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    fun hasLoginCookie(cookies: String?): Boolean {
        if (cookies == null || cookieNames.isEmpty()) return false
        for (required in cookieNames) {
            var found = false
            for (cookie in cookies.split(";")) {
                val part = cookie.trim()
                if (part.startsWith("$required=") && part.length > required.length + 1) {
                    found = true
                }
            }
            if (!found) return false
        }
        return true
    }

    companion object {
        @JvmStatic
        fun byId(id: String?): ConfigAuthProvider? {
            for (provider in values()) {
                if (provider.id == id) return provider
            }
            return null
        }

        @JvmStatic
        fun isConfigSource(source: SourceBean?): Boolean {
            return source != null && source.type == 3 &&
                    listOf("csp_PanConfigGuard", "csp_PanConfig", "csp_Config").contains(source.api)
        }

        @JvmStatic
        fun supportsMobileApi(source: SourceBean?): Boolean {
            return source != null && source.type == 3 &&
                    listOf("csp_PanConfigGuard", "csp_PanConfig").contains(source.api)
        }

        @JvmStatic
        fun isQrAction(source: SourceBean?, action: String?, title: String?): Boolean {
            if (!isConfigSource(source) || action.isNullOrEmpty() || title == null) return false
            return title.contains("扫码") || title.contains("二维码") || title.endsWith("注册")
        }

        @JvmStatic
        fun fromTitle(title: String?): ConfigAuthProvider? {
            if (title == null) return null
            val lower = title.lowercase(Locale.ROOT)
            if (title.contains("夸克")) return QUARK
            if (lower.contains("uc")) return if (lower.contains("token")) UC_TOKEN else UC
            if (title.contains("百度")) return BAIDU
            if (title.contains("哔哩") || lower.contains("bili")) return BILI
            if (title.contains("115")) return PAN115
            if (title.contains("天翼")) return PAN189
            if (title.contains("光鸭")) return GUANGYA
            if (title.contains("迅雷")) return THUNDER
            if (title.contains("移动")) return PAN139
            return null
        }
    }
}
