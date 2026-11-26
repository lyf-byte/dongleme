package com.dongleme.backend.service;

import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 极简 SSE 连接管理：每个用户可同时持有多条连接（多标签页/多设备）。
 * 只做服务端单向推送，客户端断线后由 EventSource 自动重连。
 */
@Service
public class SseService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public SseService() {
        heartbeat.scheduleAtFixedRate(this::ping, 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true), MediaType.APPLICATION_JSON));
        } catch (IOException error) {
            cleanup.run();
        }
        return emitter;
    }

    public void send(String userId, String event, Object data) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception error) {
                remove(userId, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 连接已断开
                }
            }
        }
    }

    private void ping() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception error) {
                    remove(userId, emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 连接已断开
                    }
                }
            }
        });
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(userId);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        emitters.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 应用退出
            }
        }));
        emitters.clear();
    }
}
