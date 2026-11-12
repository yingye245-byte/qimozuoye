package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物品分类实体（支持一级/二级分类，parent_id = 0 为顶级）
 */
@Data
@TableName("category")
public class Category implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 父分类 ID，0 表示顶级分类 */
    private Long parentId;

    private Integer sort;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 是否为二级分类（不映射数据库） */
    public boolean isChild() {
        return parentId != null && parentId > 0;
    }

    /** 顶级分类名（树形渲染时填充，不映射数据库） */
    private transient String parentName;

    /** 顶级分类 ID（树形渲染时填充，不映射数据库） */
    private transient Long topId;

    /** 子分类列表（树形渲染时填充，不映射数据库） */
    private transient java.util.List<Category> children;
}