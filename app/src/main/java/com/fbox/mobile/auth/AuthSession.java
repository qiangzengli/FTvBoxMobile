package com.fbox.mobile.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** A single source/provider login session; opening an app is never treated as login success. */
public final class AuthSession implements AutoCloseable {
    public interface Backend { JSONObject request(String action, Map<String, String> fields) throws Exception; }
    public interface Listener {
        void ready(String url);
        void waiting();
        void success();
        void failed(String message);
    }
    private final Backend backend;
    private final String provider;
    private final ScheduledExecutorService worker;
    private final Executor ui;
    private final Listener listener;
    private final long pollMillis, lifetimeMillis;
    private volatile boolean closed;
    private boolean started;
    private String task = "";
    private long startedAt;
    private int failures;

    public AuthSession(Backend backend, String provider, ScheduledExecutorService worker, Executor ui, Listener listener) {
        this(backend, provider, worker, ui, listener, 1500, 300000);
    }

    public AuthSession(Backend backend, String provider, ScheduledExecutorService worker, Executor ui,
                       Listener listener, long pollMillis, long lifetimeMillis) {
        this.backend = backend; this.provider = provider; this.worker = worker; this.ui = ui;
        this.listener = listener; this.pollMillis = pollMillis; this.lifetimeMillis = lifetimeMillis;
    }

    public synchronized void start() {
        if (started || closed) return;
        started = true;
        worker.execute(() -> {
            startedAt = System.nanoTime();
            try {
                JSONObject result = backend.request("loginStart", Collections.singletonMap("provider", provider));
                task = result.optString("taskId");
                if (closed) { cancelTask(); return; }
                if (result.optInt("code", -1) != 0 || task.isEmpty() || result.optString("qrUrl").isEmpty()) {
                    fail("当前线路没有提供可用的手机授权会话，请使用其他登录方式"); return;
                }
                String url = result.getString("qrUrl");
                deliver(() -> listener.ready(url));
                schedulePoll();
            } catch (Exception e) { fail("无法创建授权会话，请重试或使用其他登录方式"); }
        });
    }

    private Map<String, String> fields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("provider", provider); fields.put("taskId", task);
        return fields;
    }

    private synchronized void schedulePoll() {
        if (!closed) worker.schedule(this::poll, pollMillis, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (closed || task.isEmpty()) return;
        if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= lifetimeMillis) {
            fail("授权已超时，请重新发起登录"); return;
        }
        try {
            JSONObject response = backend.request("loginPoll", fields());
            if (closed) return;
            failures = 0;
            if ("success".equals(response.optString("status"))) {
                task = "";
                deliver(listener::success);
                finishWorker();
                return;
            }
            if (response.optBoolean("terminal") || response.optInt("code", 0) != 0) {
                fail("授权未完成或已失效，请重新发起登录"); return;
            }
            deliver(listener::waiting);
        } catch (Exception e) {
            if (++failures >= 3) { fail("授权状态读取失败，请检查网络后重试"); return; }
        }
        schedulePoll();
    }

    private void deliver(Runnable callback) { ui.execute(() -> { if (!closed) callback.run(); }); }

    private void fail(String message) {
        cancelTask();
        if (!closed) deliver(() -> listener.failed(message));
        finishWorker();
    }

    private synchronized void finishWorker() { worker.shutdown(); }

    private void cancelTask() {
        if (task.isEmpty()) return;
        try { backend.request("loginCancel", fields()); } catch (Exception ignored) { }
        task = "";
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (!worker.isShutdown()) {
            // Queued behind loginStart: a late-created session is cancelled too.
            worker.execute(this::cancelTask);
            worker.shutdown();
        }
    }
}
