package com.fbox.mobile.auth;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

/** Local transport. Credentials stay in the source plugin's existing store. No request logging. */
public final class PluginAuthClient implements AuthSession.Backend {
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build();
    private final SourceBean source;
    private final String base;

    public PluginAuthClient(SourceBean source) {
        this.source = source;
        base = ControlManager.get().getAddress(true);
    }

    @Override public JSONObject request(String action, Map<String, String> fields) throws Exception {
        if (!ConfigAuthProvider.supportsMobileApi(source)
                || ApiConfig.get().getSource(source.getKey()) != source) {
            throw new IOException("配置线路已切换或不支持手机授权，请重新打开配置中心");
        }
        HttpUrl address = HttpUrl.parse(base);
        if (address == null || !("127.0.0.1".equals(address.host()) || "localhost".equals(address.host()))) {
            throw new IOException("本机配置服务不可用，请重试");
        }
        HttpUrl.Builder url = address.newBuilder().encodedPath("/proxy").query(null)
                .addQueryParameter("do", "pan").addQueryParameter("siteKey", source.getKey())
                .addQueryParameter("action", action);
        for (Map.Entry<String, String> field : fields.entrySet()) url.addQueryParameter(field.getKey(), field.getValue());
        try (Response response = HTTP.newCall(new Request.Builder().url(url.build()).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("配置服务暂时不可用，请重试");
            // Login QR data can be embedded PNG; still bound malformed plugin responses.
            okio.BufferedSource input = response.body().source();
            input.request(1024 * 1024 + 1);
            if (input.buffer().size() > 1024 * 1024) throw new IOException("配置服务响应异常");
            return new JSONObject(input.readUtf8());
        }
    }
}
