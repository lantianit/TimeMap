package com.maptrace.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final OptionalJwtInterceptor optionalJwtInterceptor;
    private final AdminWebAuthInterceptor adminWebAuthInterceptor;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
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
                        "/api/photo/district-ranking",
                        "/api/photo/footprint",
                        "/api/follow/count",
                        "/api/comment/list",
                        "/api/comment/replies"
                )
                .order(1);
        
        // 必须登录的接口
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/actuator/**",
                        "/api/auth/login",
                        "/api/admin/auth/login",
                        "/api/photo/nearby",
                        "/api/photo/detail/**",
                        "/api/photo/batch",
                        "/api/photo/community",
                        "/api/photo/stats",
                        "/api/photo/user/**",
                        "/api/photo/district-ranking",
                        "/api/photo/footprint",
                        "/api/follow/count",
                        "/api/comment/list",
                        "/api/comment/replies",
                        "/ws/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                )
                .order(2);
    }

    /**
     * 强制 JSON 响应使用 UTF-8，避免微信等客户端乱码
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                jacksonConverter.setSupportedMediaTypes(List.of(
                        new MediaType("application", "json", StandardCharsets.UTF_8),
                        new MediaType("application", "*+json", StandardCharsets.UTF_8)));
                break;
            }
        }
    }
}
