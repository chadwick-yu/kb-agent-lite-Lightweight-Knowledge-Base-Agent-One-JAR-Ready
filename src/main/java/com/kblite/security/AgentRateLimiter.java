package com.kblite.security;

import com.kblite.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话频率限制器（单机内存滑动窗口）
 * 限制维度：每分钟请求数 / 每日请求数
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRateLimiter {

    private final SecurityProperties securityProperties;

    /** 每分钟限流窗口：userId → 计数器 */
    private final Map<Long, WindowCounter> minuteCounters = new ConcurrentHashMap<>();

    /** 每日限流：userId_date → 累计计数 */
    private final Map<String, AtomicInteger> dailyCounters = new ConcurrentHashMap<>();

    /**
     * 检查用户是否可以继续对话
     */
    public RateLimitResult check(Long userId) {
        SecurityProperties.RateLimit config = securityProperties.getRateLimit();
        if (!config.isEnabled()) {
            return RateLimitResult.allow();
        }

        if (!checkMinuteLimit(userId, config)) {
            log.warn("[RateLimiter] 每分钟限流命中 - userId: {}, 上限: {}/分钟",
                    userId, config.getMaxRequestsPerMinute());
            return RateLimitResult.deny(config.getLimitMessage());
        }
        if (!checkDailyLimit(userId, config)) {
            log.warn("[RateLimiter] 每日限流命中 - userId: {}, 上限: {}/天",
                    userId, config.getMaxRequestsPerDay());
            return RateLimitResult.deny("您今日的对话次数已达上限（" + config.getMaxRequestsPerDay() + "次），请明天再试。");
        }
        return RateLimitResult.allow();
    }

    private boolean checkMinuteLimit(Long userId, SecurityProperties.RateLimit config) {
        long now = System.currentTimeMillis();
        long windowMs = config.getWindowSeconds() * 1000L;

        WindowCounter counter = minuteCounters.compute(userId, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMs) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return counter.count.get() <= config.getMaxRequestsPerMinute();
    }

    private boolean checkDailyLimit(Long userId, SecurityProperties.RateLimit config) {
        String dateStr = LocalDate.now().toString();
        String key = userId + "_" + dateStr;

        AtomicInteger counter = dailyCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();

        if (current == 1 && dailyCounters.size() > 100) {
            dailyCounters.entrySet().removeIf(entry -> !entry.getKey().endsWith(dateStr));
            log.info("[RateLimiter] 清理过期每日计数器，剩余: {}", dailyCounters.size());
        }
        return current <= config.getMaxRequestsPerDay();
    }

    private static class WindowCounter {
        final long windowStart;
        final AtomicInteger count;

        WindowCounter(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final String denyMessage;

        private RateLimitResult(boolean allowed, String denyMessage) {
            this.allowed = allowed;
            this.denyMessage = denyMessage;
        }

        public static RateLimitResult allow() {
            return new RateLimitResult(true, null);
        }

        public static RateLimitResult deny(String message) {
            return new RateLimitResult(false, message);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getDenyMessage() {
            return denyMessage;
        }
    }
}
