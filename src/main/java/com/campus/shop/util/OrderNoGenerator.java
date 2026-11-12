package com.campus.shop.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器：SO + yyyyMMddHHmmss + 4 位随机
 * 加随机后缀是为了避免高并发下同一秒内重复（配合 order_no 唯一索引兜底）
 */
public final class OrderNoGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {}

    public static String next() {
        StringBuilder sb = new StringBuilder("SO");
        sb.append(LocalDateTime.now().format(FMT));
        for (int i = 0; i < 4; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}