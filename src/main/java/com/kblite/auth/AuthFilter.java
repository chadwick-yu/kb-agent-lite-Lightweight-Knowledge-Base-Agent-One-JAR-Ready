package com.kblite.auth;

import com.kblite.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API 鉴权过滤器
 * 拦截所有 /api/** 请求（除登录与健康检查），支持 Authorization: Bearer 头或 ?token= 查询参数
 * （?token= 用于文档预览等无法携带请求头的直链场景）
 *
 * @author kb-agent-lite
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/chat/health"
    );

    private final AuthTokenService authTokenService;
    private final AppProperties appProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 静态页面与根路径不拦截（由前端测试页自行处理登录态）
        return !uri.startsWith("/api/")
                || WHITE_LIST.stream().anyMatch(uri::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!appProperties.getAuth().isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String token = extractToken(request);
        if (authTokenService.verify(token)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return request.getParameter("token");
    }
}
