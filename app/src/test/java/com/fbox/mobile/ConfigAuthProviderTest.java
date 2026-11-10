package com.fbox.mobile;

import com.fbox.mobile.auth.ConfigAuthProvider;
import com.github.tvbox.osc.bean.SourceBean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigAuthProviderTest {
    @Test public void providerUrlsCannotEscapeTheOfficialHttpsDomain() {
        assertTrue(ConfigAuthProvider.BAIDU.allows("https://wappass.baidu.com/wp/?token=example"));
        assertTrue(ConfigAuthProvider.QUARK.allows("https://su.quark.cn/4_test?token=example"));
        for (String url : new String[]{"http://pan.baidu.com/", "https://baidu.com.evil.test/", "https://evilbaidu.com/",
                "https://baidu.com@evil.test/", "https://user@baidu.com/", "https://baidu.com:8443/",
                "intent://login#Intent;package=com.evil;end", "javascript:alert(1)", "file:///etc/passwd",
                "content://com.fbox.fbox/private", "https://baidu.com\\@evil.test/", "https://127.0.0.1/"}) {
            assertFalse(url, ConfigAuthProvider.BAIDU.allows(url));
        }
    }

    @Test public void onlyConfigurationQrActionsAreAdapted() {
        SourceBean source = new SourceBean(); source.setType(3); source.setApi("csp_PanConfigGuard");
        assertTrue(ConfigAuthProvider.isQrAction(source, "opaque-action", "夸克扫码登录"));
        assertTrue(ConfigAuthProvider.isQrAction(source, "opaque-action", "UC TV Token 扫码"));
        assertFalse(ConfigAuthProvider.isQrAction(source, "clear", "清除夸克 Cookie"));
        assertFalse(ConfigAuthProvider.isQrAction(source, "manual", "夸克 Cookie 手动输入"));
        assertFalse(ConfigAuthProvider.isQrAction(source, null, "百度扫码登录"));
        source.setApi("csp_Video"); source.setName("配置中心");
        assertFalse(ConfigAuthProvider.isQrAction(source, "play", "夸克扫码登录"));
        source.setApi("csp_Config");
        assertTrue(ConfigAuthProvider.isQrAction(source, "login", "扫码登录"));
        assertFalse(ConfigAuthProvider.supportsMobileApi(source));
    }

    @Test public void tokenAndCookieFlowsStaySeparate() {
        assertSame(ConfigAuthProvider.UC_TOKEN, ConfigAuthProvider.fromTitle("UC TV Token 扫码"));
        assertSame(ConfigAuthProvider.UC, ConfigAuthProvider.fromTitle("UC Cookie 扫码"));
        assertSame(ConfigAuthProvider.PAN115, ConfigAuthProvider.fromTitle("115扫码登录"));
        assertNull(ConfigAuthProvider.fromTitle("未知平台扫码登录"));
        assertFalse(ConfigAuthProvider.UC_TOKEN.hasLoginCookie("__puus=anything"));
        assertFalse(ConfigAuthProvider.BAIDU.hasLoginCookie("BAIDUID=anonymous; BDUSS="));
        assertTrue(ConfigAuthProvider.BAIDU.hasLoginCookie("BAIDUID=anonymous; BDUSS=example"));
        assertFalse(ConfigAuthProvider.PAN115.hasLoginCookie("UID=example; CID=example"));
        assertTrue(ConfigAuthProvider.PAN115.hasLoginCookie("UID=example; CID=example; SEID=example"));
        assertFalse(ConfigAuthProvider.BILI.hasLoginCookie("NOT_SESSDATA=example"));
        assertFalse(ConfigAuthProvider.QUARK.supportsWebLogin());
        assertFalse(ConfigAuthProvider.UC.supportsWebLogin());
        assertFalse(ConfigAuthProvider.UC_TOKEN.supportsWebLogin());
        assertTrue(ConfigAuthProvider.BAIDU.supportsWebLogin());
    }
}
