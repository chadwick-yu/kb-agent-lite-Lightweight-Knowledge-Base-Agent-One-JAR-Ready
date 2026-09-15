package com.kblite.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kblite.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 访问令牌服务：HMAC-SHA256 自包含 token（payload.signature）
 * payload = base64({"sub":"admin","exp":到期秒})，无服务端会话存储
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HMAC_ALG = "HmacSHA256";

    private final AppProperties appProperties;

    /**
     * 校验口令并签发 token
     */
    public String login(String password) {
        String expected = appProperties.getAuth().getPassword();
        if (password == null || !constantTimeEquals(password, expected)) {
            return null;
        }
        long exp = System.currentTimeMillis() / 1000 + appProperties.getAuth().getTokenExpireHours() * 3600L;
        String payload = "{\"sub\":\"admin\",\"exp\":" + exp + "}";
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sign = hmac(payloadB64);
        return payloadB64 + "." + sign;
    }

    /**
     * 校验 token，有效返回 true
     */
    public boolean verify(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        int idx = token.lastIndexOf('.');
        if (idx <= 0) {
            return false;
        }
        String payloadB64 = token.substring(0, idx);
        String sign = token.substring(idx + 1);
        if (!constantTimeEquals(sign, hmac(payloadB64))) {
            return false;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            JsonNode payload = MAPPER.readTree(payloadBytes);
            long exp = payload.path("exp").asLong(0);
            return System.currentTimeMillis() / 1000 < exp;
        } catch (Exception e) {
            log.debug("[Auth] token解析失败: {}", e.getMessage());
            return false;
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                    appProperties.getAuth().getTokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("token签名失败", e);
        }
    }

    /** 常数时间字符串比较，防时序攻击 */
    private boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ba, bb);
    }
}
