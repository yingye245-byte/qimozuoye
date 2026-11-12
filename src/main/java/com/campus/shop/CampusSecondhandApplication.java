package com.campus.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园二手闲置交易平台 - 启动类
 * Spring Boot 4.x / JDK17 环境
 */
@EnableScheduling
@SpringBootApplication
public class CampusSecondhandApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusSecondhandApplication.class, args);
    }
}
