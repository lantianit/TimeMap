package com.timemap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timemap.common.Result;
import com.timemap.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web 管理后台专用拦截器。
 * 当请求带有 X-Client-Type: web 时，使用 admin JWT 校验。
 * 否则跳过，让后续的 JwtInterceptor 处理（小程序端）。
 */
@Component
@RequiredArgsConstructor
public class AdminWebAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientType = request.getHeader("X-Client-Type");
        if (!"web".equals(clientType)) {
            // Not a web request, let other interceptors handle it
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, 401, "未提供Token");
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isAdminTokenValid(token)) {
            writeError(response, 401, "Token无效或已过期");
            return false;
        }

        Long adminId = jwtUtil.getAdminIdFromToken(token);
        String role = jwtUtil.getAdminRoleFromToken(token);

        // Set admin info into request attributes
        request.setAttribute("adminAccountId", adminId);
        request.setAttribute("adminRole", role);
        // Also set userId for compatibility with existing admin APIs
        // Admin APIs use @RequestAttribute("userId") — we map adminId here
        request.setAttribute("userId", adminId);
        request.setAttribute("isWebAdmin", true);

        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(status, message)));
    }
}
