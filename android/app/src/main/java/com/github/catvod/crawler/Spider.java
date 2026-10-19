package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import org.json.JSONObject;

/** Binary interface expected by CatVod DEX extensions. */
public class Spider {
    public static JSONObject empty = new JSONObject();
    protected static Context mContext;
    public String siteKey;

    public void init(Context context) throws Exception { mContext = context; }
    public void init(Context context, String extend) throws Exception { init(context); }
    public void initApi(SpiderApi api) throws Exception {}
    public String action(String action) { return null; }
    public String homeContent(boolean filter) throws Exception { return ""; }
    public String homeVideoContent() throws Exception { return ""; }
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception { return ""; }
    public String detailContent(List<String> ids) throws Exception { return ""; }
    public String searchContent(String key, boolean quick) throws Exception { return ""; }
    public String searchContent(String key, boolean quick, String pg) throws Exception { return searchContent(key, quick); }
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception { return ""; }
    public String liveContent(String url) throws Exception { return ""; }
    public boolean manualVideoCheck() throws Exception { return false; }
    public boolean isVideoFormat(String url) throws Exception { return false; }
    public Object[] proxyLocal(Map<String, String> params) throws Exception { return null; }
    public Object[] proxy(Map<String, String> params) throws Exception { return proxyLocal(params); }
    public void cancelByTag() {}
    public void destroy() {}
    public static Dns safeDns() { return Dns.SYSTEM; }
    public static OkHttpClient client() { return new OkHttpClient(); }
}
