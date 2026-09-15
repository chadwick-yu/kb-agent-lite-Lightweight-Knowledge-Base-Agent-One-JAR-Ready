package com.kblite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 模型接入配置（llm.*）
 * OpenAI 兼容协议标准入口：公有云（DeepSeek/通义/智谱）与私有化（vLLM/Ollama）仅需改配置
 *
 * @author kb-agent-lite
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String modelName = "deepseek-chat";
    private Double temperature = 0.2;
    private Integer maxTokens = 4000;
    private Integer timeoutSeconds = 60;
}
