package com.kblite.security;

import com.kblite.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体输入防火墙
 * 在用户消息发送给 LLM 之前执行安全检测，拦截 Prompt 注入攻击
 * 检测维度：输入长度截断 / 注入关键词匹配 / 可疑字符模式
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptGuard {

    private final SecurityProperties securityProperties;

    /**
     * 对用户输入进行安全检测
     *
     * @param message 用户原始消息
     * @param userId  用户ID（用于审计日志）
     * @return 检测结果，包含是否通过和处理后的消息
     */
    public GuardResult check(String message, Long userId) {
        if (message == null || message.isEmpty()) {
            return GuardResult.block("消息内容不能为空。");
        }

        SecurityProperties.Guard guard = securityProperties.getGuard();
        if (!guard.isEnabled()) {
            return GuardResult.pass(message);
        }

        // 1. 长度截断
        String sanitized = message.trim();
        if (sanitized.length() > guard.getMaxInputLength()) {
            log.warn("[PromptGuard] 消息超长截断 - userId: {}, 原始长度: {}, 截断至: {}",
                    userId, sanitized.length(), guard.getMaxInputLength());
            sanitized = sanitized.substring(0, guard.getMaxInputLength());
        }

        // 2. 注入关键词检测
        String checkText = guard.isCaseInsensitive() ? sanitized.toLowerCase() : sanitized;
        List<String> keywords = guard.getInjectionKeywords();
        for (String keyword : keywords) {
            String kw = guard.isCaseInsensitive() ? keyword.toLowerCase() : keyword;
            if (checkText.contains(kw)) {
                log.warn("[PromptGuard] 注入检测命中 - userId: {}, keyword: '{}', 消息前100字: {}",
                        userId, keyword, sanitized.substring(0, Math.min(100, sanitized.length())));
                return GuardResult.block(guard.getBlockMessage());
            }
        }

        // 3. 可疑模式检测
        if (containsSuspiciousPatterns(sanitized)) {
            log.warn("[PromptGuard] 可疑模式检测命中 - userId: {}, 消息前100字: {}",
                    userId, sanitized.substring(0, Math.min(100, sanitized.length())));
            return GuardResult.block(guard.getBlockMessage());
        }

        return GuardResult.pass(sanitized);
    }

    /**
     * 可疑模式：零宽字符、连续大量换行、不可见控制字符
     */
    private boolean containsSuspiciousPatterns(String text) {
        int consecutiveNewlines = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isZeroWidthChar(c)) {
                return true;
            }
            if (c == '\n') {
                consecutiveNewlines++;
                if (consecutiveNewlines > 5) {
                    return true;
                }
            } else {
                consecutiveNewlines = 0;
            }
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }

    private boolean isZeroWidthChar(char c) {
        return c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u200E' || c == '\u200F'
                || c == '\uFEFF' || c == '\u2060' || c == '\u2061' || c == '\u2062'
                || c == '\u2063' || c == '\u2064';
    }

    /**
     * 防火墙检测结果
     */
    public static class GuardResult {
        private final boolean passed;
        private final String message;
        private final String blockReason;

        private GuardResult(boolean passed, String message, String blockReason) {
            this.passed = passed;
            this.message = message;
            this.blockReason = blockReason;
        }

        public static GuardResult pass(String sanitizedMessage) {
            return new GuardResult(true, sanitizedMessage, null);
        }

        public static GuardResult block(String reason) {
            return new GuardResult(false, null, reason);
        }

        public boolean isPassed() {
            return passed;
        }

        public String getMessage() {
            return message;
        }

        public String getBlockReason() {
            return blockReason;
        }
    }
}
