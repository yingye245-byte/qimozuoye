package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {

    @Select("SELECT ca.*, i.title AS itemTitle, i.cover AS itemCover, i.price AS itemPrice, " +
            "       i.deal_status AS itemDealStatus, i.status AS itemStatus, u.nickname AS sellerName " +
            "FROM cart ca LEFT JOIN item i ON i.id = ca.item_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE ca.user_id = #{userId} ORDER BY ca.create_time DESC")
    List<Cart> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM cart WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}