package com.campus.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 留言/回复表单
 */
@Data
public class CommentDTO {

    private Long itemId;

    private Long commentId;

    @NotBlank(message = "内容不能为空")
    private String content;
}