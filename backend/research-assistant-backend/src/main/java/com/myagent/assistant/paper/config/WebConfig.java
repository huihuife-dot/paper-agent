package com.myagent.assistant.paper.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 全局配置类。
 *
 * 当前主要用于解决本地 HTML 页面、后续 Vue 前端访问后端接口时的跨域问题。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置跨域访问。
     *
     * 开发阶段允许所有来源访问后端接口。
     * 后期如果上线，可以改成只允许指定前端地址。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许本地测试页面和 Vue 前端访问
                .allowedOriginPatterns("*")
                // 允许常见请求方法
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // 允许所有请求头
                .allowedHeaders("*")
                // 是否允许携带 Cookie；当前项目暂时不需要登录，可以先设为 false
                .allowCredentials(false)
                .maxAge(3600);
    }
}