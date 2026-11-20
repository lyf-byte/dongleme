package com.dongleme.backend.security;

import com.dongleme.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流。
 *
 * 两个维度各自计数，任意一个超限即拒绝：
 * - 账号维度（全局）：防止对单个账号的密码爆破，默认 15 分钟内 5 次。
 * - IP 维度：防止同一来源撞库/撞手机号，默认 15 分钟内 30 次。
 *
 * 说明：计数在内存里，只对单实例有效。将来多实例部署时需要换成 Redis 之类的共享存储，
 * 否则每个实例各自放行，实际阈值会被放大 N 倍。
 */
@Component
public class LoginThrottle {

    private static final int MAX_ENTRIES = 20_000;

    private final int accountMax;
    private final int ipMax;
    private final long windowMillis;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginThrottle(
            @Value("${app.login.account-max-failures:5}") int accountMax,
            @Value("${app.login.ip-max-failures:30}") int ipMax,
            @Value("${app.login.window-seconds:900}") long windowSeconds) {
        this.accountMax = accountMax;
        this.ipMax = ipMax;
        this.windowMillis = Math.max(0, windowSeconds) * 1000L;
    }

    /** 进入登录校验前调用；超限直接抛 429，不再查库也不再比对密码 */
    public void checkAllowed(String ip, String account) {
        long waitSeconds = Math.max(
                waitSeconds(accountKey(account), accountMax),
                waitSeconds(ipKey(ip), ipMax));
        if (waitSeconds > 0) {
            long minutes = Math.max(1, (waitSeconds + 59) / 60);
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "登录尝试过于频繁，请 " + minutes + " 分钟后再试");
        }
    }

    public void recordFailure(String ip, String account) {
        increment(accountKey(account));
        increment(ipKey(ip));
    }

    /** 登录成功后清掉该账号的计数，IP 计数保留（同 IP 的其他账号仍受约束） */
    public void recordSuccess(String account) {
        attempts.remove(accountKey(account));
    }

    private long waitSeconds(String key, int max) {
        Deque<Long> bucket = attempts.get(key);
        if (bucket == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (bucket) {
            prune(bucket, now);
            if (bucket.size() < max) {
                return 0;
            }
            Long oldest = bucket.peekFirst();
            if (oldest == null) {
                return 0;
            }
            return Math.max(1, (oldest + windowMillis - now) / 1000);
        }
    }

    private void increment(String key) {
        if (attempts.size() >= MAX_ENTRIES) {
            attempts.clear();
        }
        Deque<Long> bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (bucket) {
            prune(bucket, now);
            bucket.addLast(now);
        }
    }

    private void prune(Deque<Long> bucket, long now) {
        while (!bucket.isEmpty() && bucket.peekFirst() + windowMillis <= now) {
            bucket.pollFirst();
        }
    }

    private static String accountKey(String account) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        return "acct|" + normalized;
    }

    private static String ipKey(String ip) {
        return "ip|" + (ip == null || ip.isBlank() ? "unknown" : ip);
    }
}
