package com.campus.shop.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单消息体（三个场景共用的统一 DTO，便于消费者复用与日志追踪）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {
    /** 兼容 JDK 序列化时代的旧消息（现已统一改用 JSON 传输） */
    private static final long serialVersionUID = 1L;

    public static final String TYPE_CREATED = "CREATED";   // 下单成功
    public static final String TYPE_PAID = "PAID";         // 已支付
    public static final String TYPE_CONFIRMED = "CONFIRMED";
    public static final String TYPE_FINISHED = "FINISHED";
    public static final String TYPE_CANCELED = "CANCELED"; // 超时/手动取消

    private Long orderId;
    private String orderNo;
    private Long itemId;
    private String itemTitle;
    private Long buyerId;
    private String buyerName;
    private String buyerEmail;
    private Long sellerId;
    private String sellerName;
    private String sellerEmail;
    private BigDecimal amount;
    private Integer status;
    private String type;

    /** 便于日志与幂等追踪 */
    private String traceId;
}