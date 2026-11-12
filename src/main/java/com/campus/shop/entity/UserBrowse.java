package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览记录（支撑"猜你喜欢""最近浏览"）
 */
@Data
@TableName("user_browse")
public class UserBrowse implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    private LocalDateTime browseTime;

    /* ---------- 展示字段 ---------- */

    @TableField(exist = false)
    private String itemTitle;

    @TableField(exist = false)
    private String itemCover;

    @TableField(exist = false)
    private java.math.BigDecimal itemPrice;
}