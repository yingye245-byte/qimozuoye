package com.campus.shop.dto;

import lombok.Data;

/**
 * 商品列表查询条件（统一封装分页与筛选参数）
 */
@Data
public class ItemQuery {

    private Integer page = 1;
    private Integer size = 12;
    private Long categoryId;
    private String keyword;
    private java.math.BigDecimal minPrice;
    private java.math.BigDecimal maxPrice;

    /** new默认最新 / priceAsc / priceDesc / hot最近留言多 */
    private String sort = "new";
}