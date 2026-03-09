package com.timemap.config;

import com.timemap.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 可选的 JWT 拦截器
 * 如果有 token 且有效，则解析 userId；如果没有 token 或 token 无效，则放行但不设置 userId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptionalJwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        log.info("[OptionalJwtInterceptor] 请求路径: {}, Authorization header: {}", 
                request.getRequestURI(), authHeader != null ? "存在" : "不存在");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                // Token 有效，解析 userId
                Long userId = jwtUtil.getUserIdFromToken(token);
                request.setAttribute("userId", userId);
                log.info("[OptionalJwtInterceptor] Token 有效, userId: {}", userId);
            } else {
                log.warn("[OptionalJwtInterceptor] Token 无效或已过期");
            }
        } else {
            log.info("[OptionalJwtInterceptor] 未提供 Token，以游客身份访问");
        }
        
        // 无论是否有 token，都放行
        return true;
    }
}
