package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.ItemImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemImageMapper extends BaseMapper<ItemImage> {

    @Select("SELECT * FROM item_image WHERE item_id = #{itemId} ORDER BY sort ASC")
    List<ItemImage> selectByItemId(@Param("itemId") Long itemId);
}