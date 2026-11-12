package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闲置物品实体
 * 审核状态 auditStatus: 0待审核 1通过 2驳回
 * 上架状态 status:       1在售  2下架  0删除
 * 成交状态 dealStatus:   1未成交 2已成交 3已关闭
 */
@Data
@TableName("item")
public class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者（卖家）ID */
    private Long userId;

    private Long categoryId;

    private String title;

    private String description;

    private BigDecimal price;

    /** 封面图相对路径 */
    private String cover;

    /** 0待审核 1通过 2驳回 */
    private Integer auditStatus;

    /** 1在售 2下架 0删除 */
    private Integer status;

    /** 1未成交 2已成交 3已关闭 */
    private Integer dealStatus;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ---------- 以下为连表展示字段，不落库 ---------- */

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String sellerName;

    @TableField(exist = false)
    private String sellerAvatar;

    @TableField(exist = false)
    private Integer commentCount;

    /** 浏览量（统计自 user_browse 表，用于首页热门排序） */
    @TableField(exist = false)
    private Integer viewCount;

    public String getAuditStatusName() {
        if (auditStatus == null) return "未知";
        return switch (auditStatus) {
            case 1 -> "审核通过";
            case 2 -> "审核驳回";
            default -> "待审核";
        };
    }

    public String getDealStatusName() {
        if (dealStatus == null) return "未知";
        return switch (dealStatus) {
            case 2 -> "已成交";
            case 3 -> "已关闭";
            default -> "在售";
        };
    }

    public String getStatusName() {
        if (status == null) return "未知";
        return switch (status) {
            case 2 -> "已下架";
            case 0 -> "已删除";
            default -> "在售";
        };
    }

    /** 是否可以在前台展示并可下单 */
    public boolean isOnSale() {
        return Integer.valueOf(1).equals(auditStatus)
                && Integer.valueOf(1).equals(status)
                && Integer.valueOf(1).equals(dealStatus);
    }
}