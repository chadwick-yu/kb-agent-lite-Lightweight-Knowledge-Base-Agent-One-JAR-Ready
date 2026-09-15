package com.kblite.security;

import com.kblite.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体输出脱敏器
 * 在 LLM 回复推送前端之前，检测是否泄露系统提示词或内部架构信息
 * 命中后整条消息替换为安全话术
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputSanitizer {

    private final SecurityProperties securityProperties;

    /**
     * 对 LLM 回复进行脱敏检测
     *
     * @param aiResponse LLM 原始回复
     * @param sessionId  会话ID（用于日志）
     * @return 脱敏后的回复
     */
    public String sanitize(String aiResponse, String sessionId) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return aiResponse;
        }

        SecurityProperties.OutputSanitizer config = securityProperties.getOutputSanitizer();
        if (!config.isEnabled()) {
            return aiResponse;
        }

        int hitCount = 0;
        List<String> keywords = config.getSensitiveKeywords();
        String hitKeyword = null;
        for (String keyword : keywords) {
            if (aiResponse.contains(keyword)) {
                hitCount++;
                if (hitKeyword == null) {
                    hitKeyword = keyword;
                }
            }
        }
        if (hitCount == 0) {
            return aiResponse;
        }

        // 多个关键词同时命中 → 高度疑似 SystemMessage 泄露
        if (hitCount >= 3) {
            log.warn("[OutputSanitizer] 高度疑似SystemMessage泄露 - sessionId: {}, 命中数: {}, 首个命中: '{}'",
                    sessionId, hitCount, hitKeyword);
            return config.getFullReplaceMessage();
        }

        // 单个核心指令标记命中 → 直接替换
        if (isCoreInstructionLeak(hitKeyword)) {
            log.warn("[OutputSanitizer] 核心指令泄露 - sessionId: {}, 命中: '{}'", sessionId, hitKeyword);
            return config.getFullReplaceMessage();
        }

        // 少量命中且非核心指令（可能只是讨论技术话题），放行
        log.info("[OutputSanitizer] 低风险提示 - sessionId: {}, 命中数: {}, 命中: '{}'", sessionId, hitCount, hitKeyword);
        return aiResponse;
    }

    private boolean isCoreInstructionLeak(String keyword) {
        if (keyword == null) {
            return false;
        }
        return "【核心规则】".equals(keyword)
                || "【工具速查】".equals(keyword)
                || "【铁律】".equals(keyword)
                || "【输出格式规范】".equals(keyword)
                || "你是『kb-agent-lite 知识库智能体".equals(keyword)
                || "@SystemMessage".equals(keyword);
    }
}
