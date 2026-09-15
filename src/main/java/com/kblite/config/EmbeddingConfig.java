package com.kblite.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地中文 Embedding 模型配置
 * bge-small-zh-v1.5（BAAI），ONNX 进程内推理、离线可用，512 维
 *
 * 注意：向量持久化文件与模型绑定，更换 embedding 模型后需重建向量库
 *
 * @author kb-agent-lite
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化本地中文 embedding 模型: bge-small-zh-v1.5 (512维, ONNX进程内推理)");
        return new BgeSmallZhV15EmbeddingModel();
    }
}
