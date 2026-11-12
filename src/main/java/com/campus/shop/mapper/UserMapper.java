package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 登录专用：需要取回 password 字段做 BCrypt 校验 */
    @Select("SELECT id, username, password, nickname, avatar, email, role, status, create_time, update_time " +
            "FROM `user` WHERE username = #{username} AND deleted = 0")
    User selectByUsernameForLogin(@Param("username") String username);

    /** 按角色统计用户数（ECharts 用户构成） */
    @Select("SELECT role, COUNT(*) AS cnt FROM `user` WHERE deleted = 0 GROUP BY role")
    List<Map<String, Object>> countByRole();

    /** 卖家销售额排行 TOP N */
    @Select("SELECT u.id AS userId, u.nickname AS nickname, " +
            "       COUNT(o.id) AS dealCount, IFNULL(SUM(o.amount),0) AS dealAmount " +
            "FROM orders o JOIN `user` u ON u.id = o.seller_id " +
            "WHERE o.status = 3 GROUP BY u.id, u.nickname " +
            "ORDER BY dealAmount DESC LIMIT #{limit}")
    List<Map<String, Object>> selectSellerRank(@Param("limit") int limit);
}