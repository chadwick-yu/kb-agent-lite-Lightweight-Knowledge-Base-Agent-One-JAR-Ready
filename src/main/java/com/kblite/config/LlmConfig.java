package com.kblite.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 模型 Bean 配置
 * 双模型策略：
 * - OpenAiChatModel（同步）：执行 function calling（流式模式下工具调用不稳定，降级同步）
 * - OpenAiStreamingChatModel（流式）：SSE 真流式输出
 *
 * @author kb-agent-lite
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final LlmProperties props;

    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        log.info("初始化流式模型 - baseUrl: {}, model: {}", props.getBaseUrl(), props.getModelName());
        checkConfig();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(props.getModelName())
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public OpenAiChatModel chatLanguageModel() {
        log.info("初始化同步模型 - baseUrl: {}, model: {}", props.getBaseUrl(), props.getModelName());
        checkConfig();
        return OpenAiChatModel.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(props.getModelName())
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private void checkConfig() {
        if (props.getBaseUrl() == null || props.getBaseUrl().isEmpty()) {
            log.warn("llm.base-url 未配置，请检查 application.yml 或环境变量 LLM_BASE_URL");
        }
        if (props.getApiKey() == null || props.getApiKey().isEmpty()) {
            log.warn("llm.api-key 未配置，请检查 application.yml 或环境变量 LLM_API_KEY");
        }
    }
}
