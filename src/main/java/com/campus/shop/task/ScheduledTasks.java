package com.campus.shop.task;

import com.campus.shop.common.CacheKeys;
import com.campus.shop.common.Constants;
import com.campus.shop.entity.Order;
import com.campus.shop.mapper.OrderMapper;
import com.campus.shop.mapper.TransactionStatMapper;
import com.campus.shop.service.OrderStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时任务（Spring Task + cron 表达式，任务书进阶档考点）
 *
 * <p>三个任务刚好覆盖"补偿 + 统计 + 清理"三类典型场景：</p>
 * <ol>
 *   <li>每 5 分钟：兜底扫描超时未支付订单（MQ 消息可能因 broker 重启丢失）</li>
 *   <li>每天凌晨 1 点：汇总昨日成交数据写入 transaction_stat，供 ECharts 看板使用</li>
 *   <li>每天凌晨 3 点：清理过期的 Redis 空值缓存与浏览记录</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final OrderMapper orderMapper;
    private final OrderStateService orderStateService;
    private final TransactionStatMapper transactionStatMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 任务一：超时订单兜底取消
     * 每 5 分钟执行一次（cron 表达式见下方注解）。
     * <p>与 MQ 延迟队列形成双保险：MQ 负责准实时，定时任务负责兜底。</p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scanTimeoutOrders() {
        try {
            doScanTimeoutOrders();
        } catch (Exception e) {
            log.error("[定时任务] 超时订单扫描异常", e);
        }
    }

    /**
     * 超时订单扫描的具体逻辑（抽成 public，便于管理员在后台点"立即扫描"演示，答辩时不用等 5 分钟）。
     *
     * @return 本次取消的订单数
     */
    public int doScanTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(Constants.ORDER_TIMEOUT_MINUTES);
        List<Order> timeoutList = orderMapper.selectTimeoutUnpaid(deadline);
        if (timeoutList.isEmpty()) {
            return 0;
        }
        log.info("[定时任务] 扫描到 {} 笔超时未支付订单，开始处理…", timeoutList.size());
        int success = 0;
        for (Order order : timeoutList) {
            try {
                if (orderStateService.cancelOrder(order.getId(), "超时未支付（定时任务兜底取消）")) {
                    success++;
                }
            } catch (Exception e) {
                log.error("[定时任务] 取消订单失败 orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("[定时任务] 超时订单处理完成，成功取消 {}/{} 笔", success, timeoutList.size());
        return success;
    }

    /**
     * 任务二：每日成交统计归档
     * <p>cron = "0 0 1 * * ?" 每天凌晨 1:00 执行，统计前一天的数据。</p>
     * <p>用 ON DUPLICATE KEY UPDATE 保证重复执行不会产生重复行（幂等）。</p>
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void dailyTransactionStat() {
        try {
            statYesterday();
        } catch (Exception e) {
            log.error("[定时任务] 成交统计失败", e);
        }
    }

    /**
     * 把「昨天」的成交数据归档进 transaction_stat（用 ON DUPLICATE KEY UPDATE 保证幂等）。
     *
     * @return 本次成功写入的天数（0 或 1），管理员手动触发时同样走这里
     */
    public int statYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[定时任务] 开始统计 {} 的成交数据…", yesterday);
        Map<String, Object> sum = transactionStatMapper.sumDealByDate(yesterday);
        int count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        if (sum != null) {
            Object c = sum.get("cnt");
            Object a = sum.get("amt");
            count = c == null ? 0 : ((Number) c).intValue();
            amount = a == null ? BigDecimal.ZERO : new BigDecimal(a.toString());
        }
        transactionStatMapper.upsertStat(yesterday, count, amount);
        log.info("[定时任务] {} 统计完成：成交 {} 笔，金额 ￥{}", yesterday, count, amount);
        // 统计数据变了，让看板缓存失效
        clearDashboardCache();
        return 1;
    }

    /**
     * 清空数据看板的 Redis 缓存（统计完 / 管理员手动点按钮时调用）。
     */
    public void clearDashboardCache() {
        Set<String> keys = redisTemplate.keys(CacheKeys.DASHBOARD + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[定时任务] 已清空看板缓存 {} 个", keys.size());
        }
    }

    /**
     * 任务三：清理过期缓存与浏览记录
     * <p>cron = "0 0 3 * * ?" 每天凌晨 3:00 执行。</p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredCache() {
        log.info("[定时任务] 开始清理过期缓存…");
        try {
            // 清理防穿透的空值缓存（正常情况下它会自然过期，这里兜底扫一遍）
            var nullKeys = redisTemplate.keys(CacheKeys.NULL_HOLD + "*");
            if (nullKeys != null && !nullKeys.isEmpty()) {
                redisTemplate.delete(nullKeys);
                log.info("[定时任务] 清理空值缓存 {} 个", nullKeys.size());
            }
            // 清理 7 天前的浏览记录
            var browseKeys = redisTemplate.keys(CacheKeys.BROWSE + "*");
            if (browseKeys != null && !browseKeys.isEmpty()) {
                long expired = 0;
                for (String key : browseKeys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl < 0) {
                        redisTemplate.delete(key);
                        expired++;
                    }
                }
                log.info("[定时任务] 清理无效浏览记录 {} 个", expired);
            }
        } catch (Exception e) {
            log.error("[定时任务] 缓存清理失败", e);
        }
    }

    /**
     * 任务四（演示用）：每 10 分钟打印一次系统心跳，证明定时任务生效
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void heartbeat() {
        log.info("[定时任务] ⏱ 系统心跳 {} —— 定时任务运行正常", LocalDateTime.now().format(FMT));
    }
}