package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 * 角色: 1普通用户 2认证卖家 3超级管理员
 */
@Data
@TableName("user")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码，查询时默认不返回 */
    @TableField(select = false)
    private String password;

    private String nickname;

    private String avatar;

    /** 邮箱，用于 MQ 异步发送订单通知 */
    private String email;

    /** 1普通用户 2认证卖家 3超级管理员 */
    private Integer role;

    /** 0禁用 1正常 */
    private Integer status;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 角色中文名（不映射数据库） */
    public String getRoleName() {
        if (role == null) return "未知";
        return switch (role) {
            case 3 -> "超级管理员";
            case 2 -> "认证卖家";
            default -> "普通用户";
        };
    }
}