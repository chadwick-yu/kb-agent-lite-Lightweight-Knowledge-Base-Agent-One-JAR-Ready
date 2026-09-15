package com.kblite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用级配置（app.*）
 *
 * @author kb-agent-lite
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 运行时数据目录（H2 库 / 上传文件 / 向量持久化文件） */
    private String dataDir = "./data";

    private final Auth auth = new Auth();

    @Data
    public static class Auth {
        /** 是否开启口令鉴权 */
        private boolean enabled = true;
        /** 访问口令 */
        private String password = "admin123";
        /** token HMAC 签名密钥 */
        private String tokenSecret = "kb-lite-default-secret-please-change";
        /** token 有效期（小时） */
        private int tokenExpireHours = 72;
    }
}
