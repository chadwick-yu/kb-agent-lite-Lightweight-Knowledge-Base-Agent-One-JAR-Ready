package com.kblite.auth;

import com.kblite.common.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录接口：口令校验通过后签发访问令牌
 *
 * @author kb-agent-lite
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthTokenService authTokenService;

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String password = body == null ? null : body.get("password");
        String token = authTokenService.login(password);
        if (token == null) {
            log.warn("[Auth] 登录失败：口令错误");
            return ApiResult.fail(401, "口令错误");
        }
        log.info("[Auth] 登录成功");
        return ApiResult.ok(Map.of("token", token));
    }
}
