package com.kblite.knowledge.config;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tika 解析器配置
 *
 * @author kb-agent-lite
 */
@Configuration
public class TikaConfig {

    @Bean
    public Parser tikaAutoDetectParser() {
        return new AutoDetectParser();
    }

    @Bean
    public ParseContext parseContext() {
        return new ParseContext();
    }
}
