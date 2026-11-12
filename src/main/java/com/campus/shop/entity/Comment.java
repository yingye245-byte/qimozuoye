package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 留言咨询实体（买家对物品提问，卖家回复）
 */
@Data
@TableName("comment")
public class Comment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    /** 留言人 ID */
    private Long fromUser;

    /** 被留言人（卖家）ID */
    private Long toUser;

    private String content;

    /** 卖家回复内容 */
    private String replyContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /* ---------- 展示字段 ---------- */

    @TableField(exist = false)
    private String fromUserName;

    @TableField(exist = false)
    private String fromUserAvatar;

    @TableField(exist = false)
    private String itemTitle;
}