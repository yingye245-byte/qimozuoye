package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 * 状态 status: 0待支付 1已支付 2卖家已确认 3已完成 4已取消 5已关闭
 */
@Data
@TableName("orders")
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int STATUS_UNPAID   = 0;  // 待支付
    public static final int STATUS_PAID     = 1;  // 已支付
    public static final int STATUS_CONFIRM  = 2;  // 卖家已确认
    public static final int STATUS_FINISH   = 3;  // 已完成
    public static final int STATUS_CANCEL   = 4;  // 已取消（超时/用户取消）
    public static final int STATUS_CLOSED   = 5;  // 已关闭（物品被他人买走等）

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号：SO + yyyyMMddHHmmss + 4位随机 */
    private String orderNo;

    private Long itemId;

    private Long buyerId;

    private Long sellerId;

    private BigDecimal amount;

    /** 0待支付 1已支付 2已确认 3已完成 4已取消 5已关闭 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    /** 取消/关闭原因 */
    private String cancelReason;

    /* ---------- 展示字段 ---------- */

    @TableField(exist = false)
    private String itemTitle;

    @TableField(exist = false)
    private String itemCover;

    @TableField(exist = false)
    private String buyerName;

    @TableField(exist = false)
    private String sellerName;

    @TableField(exist = false)
    private String buyerEmail;

    @TableField(exist = false)
    private String sellerEmail;

    public String getStatusName() {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PAID -> "已支付";
            case STATUS_CONFIRM -> "卖家已确认";
            case STATUS_FINISH -> "已完成";
            case STATUS_CANCEL -> "已取消";
            case STATUS_CLOSED -> "已关闭";
            default -> "待支付";
        };
    }
}