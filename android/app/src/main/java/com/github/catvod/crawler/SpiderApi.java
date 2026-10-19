package com.github.catvod.crawler;

import android.util.Log;
import android.util.Base64;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Host services exposed to CatVod extensions. */
public class SpiderApi {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();
    private final String localAddress;
    private final String remoteAddress;

    public SpiderApi(String localAddress, String remoteAddress) {
        this.localAddress = localAddress == null ? "" : localAddress;
        this.remoteAddress = remoteAddress == null ? "" : remoteAddress;
    }

    public String getAddress(boolean local) {
        String address = local ? localAddress : remoteAddress;
        return address.isEmpty() ? "" : address + "/";
    }
    public String getPort() { return localAddress.isEmpty() ? "" : "8793"; }
    public void log(String message) { Log.d("FBoxSpider", message); }
    public int getScreenOrientation() { return 1; }

    public String multiReq(JsonArray requests) {
        if (requests == null || requests.size() == 0) return "";
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(requests.size(), 6));
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (JsonElement element : requests) {
                if (element.isJsonObject()) {
                    JsonObject request = element.getAsJsonObject();
                    futures.add(executor.submit((Callable<String>) () -> request(request)));
                }
            }
            JsonArray result = new JsonArray();
            for (Future<String> future : futures) {
                String body = future.get();
                try {
                    result.add(JsonParser.parseString(body));
                } catch (RuntimeException ignored) {
                    result.add(body);
                }
            }
            return result.toString();
        } catch (Exception error) {
            Log.w("FBoxSpider", "multiReq failed", error);
            return "";
        } finally {
            executor.shutdownNow();
        }
    }

    public String webParse(String url, String flag) {
        if (url == null || url.isEmpty()) return "";
        String encoded = Base64.encodeToString(
            url.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        return "proxy://go=SuperParse&flag=" + (flag == null ? "" : flag) + "&url=" + encoded;
    }

    private static String request(JsonObject request) {
        try {
            String url = string(request, "url");
            if (url.isEmpty()) return "";
            Request.Builder builder = new Request.Builder().url(url);
            JsonElement headers = request.get("headers");
            if (headers != null && headers.isJsonObject()) {
                for (Map.Entry<String, JsonElement> header : headers.getAsJsonObject().entrySet()) {
                    builder.header(header.getKey(), header.getValue().getAsString());
                }
            }
            if ("POST".equalsIgnoreCase(string(request, "method"))) {
                JsonElement data = request.get("data");
                RequestBody body;
                if ("form".equalsIgnoreCase(string(request, "postType")) && data != null && data.isJsonObject()) {
                    FormBody.Builder form = new FormBody.Builder();
                    for (Map.Entry<String, JsonElement> field : data.getAsJsonObject().entrySet()) {
                        form.add(field.getKey(), field.getValue().getAsString());
                    }
                    body = form.build();
                } else {
                    String value = data == null || data.isJsonNull() ? "" :
                        data.isJsonPrimitive() ? data.getAsString() : data.toString();
                    body = RequestBody.create(value, (MediaType) null);
                }
                builder.post(body);
            }
            try (Response response = CLIENT.newCall(builder.build()).execute()) {
                return response.body() == null ? "" : response.body().string();
            }
        } catch (IOException | RuntimeException error) {
            Log.w("FBoxSpider", "multiReq request failed", error);
            return "";
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
