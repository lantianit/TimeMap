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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 可选登录的接口：有 token 就解析，没有也放行
        registry.addInterceptor(optionalJwtInterceptor)
                .addPathPatterns(
                        "/api/photo/nearby",
                        "/api/photo/detail/**",
                        "/api/photo/batch",
                        "/api/photo/community",
                        "/api/photo/stats",
                        "/api/comment/list",
                        "/api/comment/replies"
                )
                .order(1);
        
        // 必须登录的接口
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/photo/nearby",
                        "/api/photo/detail/**",
                        "/api/photo/batch",
                        "/api/photo/community",
                        "/api/photo/stats",
                        "/api/comment/list",
                        "/api/comment/replies"
                )
                .order(2);
    }
}
