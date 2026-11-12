package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.UserBrowse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserBrowseMapper extends BaseMapper<UserBrowse> {

    /**
     * 最近浏览（每个商品只保留最近一次）。
     * <p>MySQL 8 默认开启 ONLY_FULL_GROUP_BY，`SELECT b.* ... GROUP BY b.item_id` 会报
     * "Expression #1 of SELECT list is not in GROUP BY clause"；这里改成
     * 「先按 item_id 取最近一条 id，再回表」的写法，语义相同且不依赖 sql_mode。</p>
     */
    @Select("SELECT b.*, i.title AS itemTitle, i.cover AS itemCover, i.price AS itemPrice " +
            "FROM user_browse b " +
            "JOIN (SELECT item_id, MAX(id) AS max_id FROM user_browse " +
            "      WHERE user_id = #{userId} AND item_id <> 0 GROUP BY item_id) t ON t.max_id = b.id " +
            "LEFT JOIN item i ON i.id = b.item_id " +
            "ORDER BY b.browse_time DESC LIMIT #{limit}")
    List<UserBrowse> selectRecent(@Param("userId") Long userId, @Param("limit") int limit);
}