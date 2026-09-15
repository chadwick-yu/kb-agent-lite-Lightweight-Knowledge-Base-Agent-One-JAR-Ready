package com.kblite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * kb-agent-lite 启动类
 * 轻量级知识库问答智能体服务：Tika 解析 + 本地中文向量模型 + 内嵌向量库/H2 + OpenAI 兼容 LLM
 *
 * @author kb-agent-lite
 */
@EnableAsync
@MapperScan("com.kblite.**.mapper")
@SpringBootApplication
public class KbLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbLiteApplication.class, args);
    }
}
