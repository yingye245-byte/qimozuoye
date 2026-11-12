package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT cm.*, u.nickname AS fromUserName, u.avatar AS fromUserAvatar " +
            "FROM comment cm LEFT JOIN `user` u ON u.id = cm.from_user " +
            "WHERE cm.item_id = #{itemId} ORDER BY cm.create_time DESC")
    List<Comment> selectByItemId(@Param("itemId") Long itemId);

    /** 卖家收到的所有咨询 */
    @Select("SELECT cm.*, u.nickname AS fromUserName, i.title AS itemTitle " +
            "FROM comment cm LEFT JOIN `user` u ON u.id = cm.from_user " +
            "LEFT JOIN item i ON i.id = cm.item_id " +
            "WHERE cm.to_user = #{sellerId} ORDER BY cm.create_time DESC")
    List<Comment> selectByToUser(@Param("sellerId") Long sellerId);
}