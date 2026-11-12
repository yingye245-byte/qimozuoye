package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

/**
 * 物品图片（一个物品多张图，sort 升序展示）
 */
@Data
@TableName("item_image")
public class ItemImage implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private String url;

    private Integer sort;
}