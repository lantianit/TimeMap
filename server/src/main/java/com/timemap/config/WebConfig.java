package com.timemap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final OptionalJwtInterceptor optionalJwtInterceptor;
    private final AdminWebAuthInterceptor adminWebAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Web 管理后台拦截器（最高优先级，仅处理 X-Client-Type: web 的请求）
        registry.addInterceptor(adminWebAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login")
                .order(0);

        // 可选登录的接口：有 token 就解析，没有也放行
        registry.addInterceptor(optionalJwtInterceptor)
                .addPathPatterns(
                        "/api/photo/nearby",
                        "/api/photo/detail/**",
                        "/api/photo/batch",
                        "/api/photo/community",
                        "/api/photo/stats",
                        "/api/photo/user/**",
                        "/api/comment/list",
                        "/api/comment/replies"
                )
                .order(1);
        
        // 必须登录的接口
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/admin/auth/login",
                        "/api/photo/nearby",
                        "/api/photo/detail/**",
                        "/api/photo/batch",
                        "/api/photo/community",
                        "/api/photo/stats",
                        "/api/photo/user/**",
                        "/api/comment/list",
                        "/api/comment/replies"
                )
                .order(2);
    }
}
