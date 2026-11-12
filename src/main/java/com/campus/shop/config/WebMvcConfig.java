package com.campus.shop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射：把用户上传的图片目录（默认 D:/projects/uploads）映射到 /uploads/** 路径。
 *
 * <p>说明：该映射明确写成 "/uploads/**"，避免 application.yml 中 static-locations 的
 * 简写形式在某些情况下让 /uploads/** 被默认静态处理器（/**）抢先匹配而返回首页 HTML。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${campus.upload.path:D:/projects/uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadPath.replace("\\", "/");
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + location);
    }
}