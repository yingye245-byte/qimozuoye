package com.campus.shop.config;

import com.campus.shop.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动自检：应用启动后检查数据库 / Redis / 关键数据是否就绪，
 * 便于部署时一眼看出环境问题（答辩演示前也可以先看这段日志）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupChecker implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final CacheService cacheService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length > 0 ? String.join(",", profiles) : "default";
        log.info("================ 启动自检开始（profile = {}）================", profile);

        // 1. 数据库连通性
        try {
            List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
            Integer users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class);
            Integer items = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item WHERE status <> 0", Integer.class);
            Integer orders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
            log.info("[自检-数据库] 连接正常，表数量 = {}，用户 {} 人，在售商品 {} 件，订单 {} 笔",
                    tables.size(), users, items, orders);
            if (tables.size() < 9) {
                log.warn("[自检-数据库] 表数量少于 9 张，请确认已执行 sql/campus_secondhand.sql");
            }
        } catch (Exception e) {
            log.error("[自检-数据库] 连接或查询失败：{}", e.getMessage());
        }

        // 2. Redis 连通性
        try {
            cacheService.set("campus:health:startup", profile, 60);
            Object v = cacheService.getRaw("campus:health:startup");
            log.info("[自检-Redis] 读写正常，回写值 = {}", v);
        } catch (Exception e) {
            log.error("[自检-Redis] 连接失败（不影响页面访问，但缓存/防击穿会降级）：{}", e.getMessage());
        }

        log.info("================ 启动自检结束，访问 http://localhost:{} ================",
                environment.getProperty("server.port", "8080"));
    }
}