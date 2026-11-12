package com.campus.shop.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布/编辑商品表单
 */
@Data
public class ItemDTO {

    /** 编辑时携带，新增时为空 */
    private Long id;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 个字")
    private String title;

    @NotBlank(message = "请填写商品描述")
    @Size(max = 2000, message = "描述最多 2000 个字")
    private String description;

    @NotNull(message = "请填写价格")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    @DecimalMax(value = "999999.99", message = "价格过大")
    private BigDecimal price;

    /** 封面图路径（上传后回填，也可直接用 /images/item/xxx.jpg） */
    private String cover;

    /** 多图路径列表（表单以逗号分隔字符串提交） */
    private List<String> images = new ArrayList<>();

    /** 前端表单以逗号分隔的字符串提交多图路径 */
    public void setImages(String csv) {
        List<String> list = new ArrayList<>();
        if (csv != null && !csv.isBlank()) {
            for (String s : csv.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim());
                }
            }
        }
        this.images = list;
    }

    /** 兼容直接传 List 的场景（例如测试或 JSON 请求） */
    public void setImages(List<String> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }
}