package com.fbox.mobile.auth;

import com.github.tvbox.osc.bean.SourceBean;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** Provider capabilities, independent of the opaque action IDs used by subscription jars. */
public enum ConfigAuthProvider {
    QUARK("quark", "夸克", "setQuarkCookie", "qkRegister", "https://pan.quark.cn/", "https://pan.quark.cn/", new String[]{"__puus"}, new String[]{"quark.cn"}, "com.quark.browser"),
    UC("ucCookie", "UC", "setUcCookie", "ucRegister", "https://drive.uc.cn/", "https://drive.uc.cn/", new String[]{"__puus"}, new String[]{"uc.cn"}, "com.UCMobile"),
    UC_TOKEN("ucToken", "UC TV Token", "setUcToken", "", "", "", new String[]{}, new String[]{"uc.cn"}, "com.UCMobile"),
    BAIDU("baidu", "百度网盘", "setBaiduCookie", "baiduRegister", "https://pan.baidu.com/wap/home", "https://pan.baidu.com/", new String[]{"BDUSS"}, new String[]{"baidu.com"}, "com.baidu.netdisk"),
    BILI("bili", "哔哩哔哩", "setBiliCookie", "", "https://passport.bilibili.com/h5-app/passport/login", "https://www.bilibili.com/", new String[]{"SESSDATA"}, new String[]{"bilibili.com"}, "tv.danmaku.bili", "com.bilibili.app.in"),
    PAN115("pan115", "115网盘", "setPan115Cookie", "", "https://passport.115.com/", "https://115.com/", new String[]{"UID", "CID", "SEID"}, new String[]{"115.com"}, "com.ylmf.androidclient"),
    PAN189("pan189", "天翼云盘", "setPan189Cookie", "", "https://cloud.189.cn/", "https://cloud.189.cn/", new String[]{"COOKIE_LOGIN_USER"}, new String[]{"189.cn"}, "com.cn21.ecloud"),
    GUANGYA("guangya", "光鸭网盘", "setGuangYaToken", "gyRegister", "", "", new String[]{}, new String[]{"guangyapan.com"}),
    THUNDER("thunder", "迅雷", "", "xlRegister", "https://pan.xunlei.com/", "", new String[]{}, new String[]{"xunlei.com"}, "com.xunlei.downloadprovider"),
    PAN139("pan139", "移动云盘", "", "ydRegister", "https://yun.139.com/", "", new String[]{}, new String[]{"139.com"}, "com.chinamobile.mcloud");

    public final String id, label, saveAction, registerKey, loginUrl, cookieUrl;
    public final String[] cookieNames, domains, packages;

    ConfigAuthProvider(String id, String label, String saveAction, String registerKey,
                       String loginUrl, String cookieUrl, String[] cookieNames, String[] domains,
                       String... packages) {
        this.id = id; this.label = label; this.saveAction = saveAction; this.registerKey = registerKey;
        this.loginUrl = loginUrl; this.cookieUrl = cookieUrl; this.cookieNames = cookieNames;
        this.domains = domains; this.packages = packages;
    }

    public static ConfigAuthProvider byId(String id) {
        for (ConfigAuthProvider provider : values()) if (provider.id.equals(id)) return provider;
        return null;
    }

    public boolean supportsWebLogin() {
        // The mobile Quark/UC homepages redirect to app-download pages, not login forms.
        return this == BAIDU || this == BILI || this == PAN115 || this == PAN189;
    }

    public static boolean isConfigSource(SourceBean source) {
        return source != null && source.getType() == 3
                && Arrays.asList("csp_PanConfigGuard", "csp_PanConfig", "csp_Config").contains(source.getApi());
    }

    /** Only the PanConfig family exposes the observed loginStart/loginPoll contract. */
    public static boolean supportsMobileApi(SourceBean source) {
        return source != null && source.getType() == 3
                && Arrays.asList("csp_PanConfigGuard", "csp_PanConfig").contains(source.getApi());
    }

    public static boolean isQrAction(SourceBean source, String action, String title) {
        if (!isConfigSource(source) || action == null || action.isEmpty() || title == null) return false;
        return title.contains("扫码") || title.contains("二维码") || title.endsWith("注册");
    }

    public static ConfigAuthProvider fromTitle(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        if (title.contains("夸克")) return QUARK;
        if (lower.contains("uc")) return lower.contains("token") ? UC_TOKEN : UC;
        if (title.contains("百度")) return BAIDU;
        if (title.contains("哔哩") || lower.contains("bili")) return BILI;
        if (title.contains("115")) return PAN115;
        if (title.contains("天翼")) return PAN189;
        if (title.contains("光鸭")) return GUANGYA;
        if (title.contains("迅雷")) return THUNDER;
        if (title.contains("移动")) return PAN139;
        return null;
    }

    /** Never forward subscription-supplied file/content/intent URLs or unrelated hosts. */
    public boolean allows(String value) {
        if (value == null || value.length() > 16384) return false;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : domains) if (host.equals(domain) || host.endsWith("." + domain)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    public boolean hasLoginCookie(String cookies) {
        if (cookies == null || cookieNames.length == 0) return false;
        for (String required : cookieNames) {
            boolean found = false;
            for (String cookie : cookies.split(";")) {
                String part = cookie.trim();
                if (part.startsWith(required + "=") && part.length() > required.length() + 1) found = true;
            }
            if (!found) return false;
        }
        return true;
    }
}
