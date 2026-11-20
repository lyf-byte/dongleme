package com.dongleme.backend.security;

import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鉴权用户短缓存：数据库在远端，每个请求都查一次用户会额外多一次网络往返。
 */
@Service
public class UserLookupCache {

    private static final long TTL_MILLIS = 10_000;
    private static final int MAX_ENTRIES = 2_000;

    private final UserMapper userMapper;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public UserLookupCache(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User get(String id) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(id);
        if (entry != null && now - entry.at() < TTL_MILLIS) {
            return entry.user();
        }

        User user = userMapper.selectById(id);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(id, new Entry(user, now));
        return user;
    }

    public void invalidate(String id) {
        cache.remove(id);
    }

    private record Entry(User user, long at) {
    }
}
