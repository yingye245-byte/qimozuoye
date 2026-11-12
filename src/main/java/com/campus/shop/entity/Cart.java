package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购物车（收藏/想要清单）
 */
@Data
@TableName("cart")
public class Cart implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /* ---------- 展示字段 ---------- */

    @TableField(exist = false)
    private String itemTitle;

    @TableField(exist = false)
    private String itemCover;

    @TableField(exist = false)
    private java.math.BigDecimal itemPrice;

    @TableField(exist = false)
    private Integer itemDealStatus;

    @TableField(exist = false)
    private String sellerName;
}