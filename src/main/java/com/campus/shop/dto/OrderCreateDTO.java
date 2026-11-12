package com.campus.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单请求
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "商品不能为空")
    private Long itemId;

    /** 留言给卖家（可空） */
    private String remark;
}