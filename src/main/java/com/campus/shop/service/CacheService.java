package com.campus.shop.service;

import com.campus.shop.common.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存服务 —— 统一封装"防穿透 + 防击穿 + 随机过期"三件套
 * <p>任务书进阶档要求 Redis 用于缓存与登录状态，高阶级还要求处理缓存三大问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 空值占位对象，用于缓存穿透时放置"空标记" */
    private static final String NULL_FLAG = "__NULL__";

    /* ==================== 基础读写 ==================== */

    public <T> T get(String key, Class<T> clazz) {
        Object v = redisTemplate.opsForValue().get(key);
        if (v == null) return null;
        if (NULL_FLAG.equals(v)) return null;
        return clazz.isInstance(v) ? clazz.cast(v) : null;
    }

    public Object getRaw(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 写入缓存，并给 TTL 加 0~5 分钟随机抖动
     * <p><b>防止缓存雪崩</b>：若大量 key 同时设置相同过期时间，
     * 到点全部失效会瞬间压垮数据库。随机抖动让失效时间打散。</p>
     */
    public void set(String key, Object value, long ttlSeconds) {
        long jitter = ThreadLocalRandom.current().nextLong(0, 300);
        redisTemplate.opsForValue().set(key, value, ttlSeconds + jitter, TimeUnit.SECONDS);
    }

    public void setNoJitter(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /* ==================== 防穿透 + 防击穿 核心方法 ==================== */

    /**
     * 带完整保护的缓存读取。
     *
     * <pre>
     * 流程：
     *   1. 查 Redis，命中直接返回                    → 快
     *   2. 命中"空标记"直接返回 null                 → 防穿透（查不存在的商品不会每次都打到 DB）
     *   3. 未命中获取互斥锁；
     *      拿锁成功 → Double Check + 查 DB + 回填缓存 → 防击穿（热点 key 失效瞬间只有一个线程查 DB）
     *      拿锁失败 → 短暂自旋等待已有结果，超时后直接查 DB（降级）
     * </pre>
     *
     * @param key        缓存 key
     * @param clazz      返回类型
     * @param ttl        正常数据缓存秒数
     * @param loader     回源函数（查数据库）
     */
    public <T> T getWithProtect(String key, Class<T> clazz, long ttl, Supplier<T> loader) {
        // 1. 命中缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_FLAG.equals(cached)) {
                return null;
            }
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
        }

        String lockKey = CacheKeys.LOCK + key;
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CacheKeys.LOCK_TTL, TimeUnit.SECONDS));

        if (locked) {
            try {
                // Double Check：可能在等锁期间别的线程已经写好了
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (NULL_FLAG.equals(cached)) return null;
                    if (clazz.isInstance(cached)) return clazz.cast(cached);
                }

                T value = loader.get();
                if (value == null) {
                    // 防穿透：数据不存在也缓存一个空标记
                    setNoJitter(key, NULL_FLAG, CacheKeys.NULL_HOLD_TTL);
                } else {
                    set(key, value, ttl);
                }
                return value;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // 没抢到锁：自旋最多 500ms 等下别的线程写缓存
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (NULL_FLAG.equals(cached)) return null;
                if (clazz.isInstance(cached)) return clazz.cast(cached);
            }
        }
        // 仍然没有：降级直接查库（保证可用性）
        log.debug("缓存自旋超时，降级查询数据库 key={}", key);
        return loader.get();
    }

    /* ==================== 浏览记录（Redis List） ==================== */

    public void pushBrowse(Long userId, Long itemId) {
        String key = CacheKeys.BROWSE + userId;
        redisTemplate.opsForList().remove(key, 0, String.valueOf(itemId));
        redisTemplate.opsForList().leftPush(key, String.valueOf(itemId));
        redisTemplate.opsForList().trim(key, 0, 19);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }
}