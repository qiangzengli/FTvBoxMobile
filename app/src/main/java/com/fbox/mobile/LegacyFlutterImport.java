package com.fbox.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One-time, non-destructive import from the previously installed Flutter app. */
public final class LegacyFlutterImport {
    private static final String LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu";

    public static void run(Context context) {
        SharedPreferences state = context.getSharedPreferences("native_migration", Context.MODE_PRIVATE);
        if (state.getBoolean("flutter_imported", false)) return;
        SharedPreferences legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
        try {
            String selected = legacy.getString("flutter.source.current", "");
            String selectedSubscription = selected.contains("::") ? selected.substring(0, selected.indexOf("::")) : "";
            JsonArray subscriptions = array(legacy.getString("flutter.subscriptions", "[]"));
            String activeUrl = "";
            String activeRoot = "";
            ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<>());
            for (JsonElement entry : subscriptions) {
                JsonObject sub = entry.getAsJsonObject();
                String root = text(sub, "url");
                String line = text(sub, "selectedUrl");
                String effective = line.isEmpty() ? root : line;
                if (effective.isEmpty()) continue;
                if (!history.contains(root)) history.add(root);
                if (!history.contains(effective)) history.add(effective);
                if (activeUrl.isEmpty() || selectedSubscription.equals(text(sub, "id"))) {
                    activeUrl = effective;
                    activeRoot = root;
                }
            }
            if (!Hawk.contains(HawkConfig.API_URL) && !activeUrl.isEmpty()) {
                Hawk.put(HawkConfig.API_URL, activeUrl);
                Hawk.put(HawkConfig.API_HISTORY, history);
                Hawk.put(HawkConfig.HOME_API, sourceKey(selected));
                if (!activeRoot.equals(activeUrl)) {
                    Hawk.put(HawkConfig.API_LINE_SOURCE, activeRoot);
                    ArrayList<String> lines = new ArrayList<>();
                    lines.add(HistoryHelper.buildApiLine("已选线路", activeUrl));
                    Hawk.put(HawkConfig.API_LINE_LIST, lines);
                }
            }
            Map<String, VodInfo> media = new HashMap<>();
            for (JsonElement entry : array(legacy.getString("flutter.library.media", "[]"))) {
                JsonObject item = entry.getAsJsonObject();
                String key = text(item, "sourceKey");
                String id = text(item, "id");
                if (key.isEmpty() || id.startsWith("msearch:")) continue;
                VodInfo vod = new VodInfo();
                vod.id = text(item, "detailId").isEmpty() ? id : text(item, "detailId");
                vod.sourceKey = sourceKey(key);
                vod.name = text(item, "title");
                vod.pic = text(item, "posterUrl");
                vod.note = text(item, "note");
                vod.type = text(item, "category");
                vod.actor = text(item, "actors");
                vod.director = text(item, "director");
                vod.des = text(item, "description");
                media.put(key + "::" + id, vod);
            }
            List<String> watched = stringList(legacy.getString("flutter.library.history", ""));
            // Insert oldest first so the reference app's descending timestamps preserve order.
            for (int i = watched.size() - 1; i >= 0; i--) {
                VodInfo vod = media.get(watched.get(i));
                if (vod != null && RoomDataManger.getVodInfo(vod.sourceKey, vod.id) == null) {
                    RoomDataManger.insertVodRecord(vod.sourceKey, vod);
                }
            }
            for (String id : stringList(legacy.getString("flutter.library.favorites", ""))) {
                VodInfo vod = media.get(id);
                if (vod != null) RoomDataManger.insertVodCollect(vod.sourceKey, vod);
            }
            state.edit().putBoolean("flutter_imported", true).apply();
        } catch (Exception error) {
            // Keep every legacy preference and permit retry after a failed import.
            Log.e("FBoxMigration", "Flutter data import could not finish", error);
        }
    }

    static String sourceKey(String identity) {
        int split = identity.indexOf("::");
        return split < 0 ? identity : identity.substring(split + 2);
    }

    private static JsonArray array(String json) { return JsonParser.parseString(json).getAsJsonArray(); }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    static List<String> stringList(String stored) throws Exception {
        ArrayList<String> values = new ArrayList<>();
        if (stored.isEmpty()) return values;
        if (stored.startsWith(LIST_PREFIX + "!")) stored = stored.substring(LIST_PREFIX.length() + 1);
        if (stored.startsWith("[")) {
            for (JsonElement value : array(stored)) values.add(value.getAsString());
        } else if (stored.startsWith(LIST_PREFIX)) {
            byte[] bytes = Base64.decode(stored.substring(LIST_PREFIX.length()), Base64.DEFAULT);
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object decoded = input.readObject();
                if (decoded instanceof List<?>) {
                    for (Object value : (List<?>) decoded) if (value instanceof String) values.add((String) value);
                }
            }
        }
        return values;
    }
}
