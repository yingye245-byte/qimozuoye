package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.shop.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 前台商品分页查询（只查审核通过 + 在售 + 未成交的）
     */
    @Select("<script>" +
            "SELECT i.*, c.name AS categoryName, u.nickname AS sellerName, u.avatar AS sellerAvatar, " +
            "      (SELECT COUNT(*) FROM comment cm WHERE cm.item_id = i.id) AS commentCount " +
            "FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE i.deleted = 0 AND i.audit_status = 1 AND i.status = 1 AND i.deal_status = 1 " +
            "<if test='categoryId != null'> AND (i.category_id = #{categoryId} " +
            "     OR i.category_id IN (SELECT sc.id FROM category sc WHERE sc.parent_id = #{categoryId})) </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (i.title LIKE CONCAT('%',#{keyword},'%') " +
            "     OR i.description LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='minPrice != null'> AND i.price &gt;= #{minPrice} </if>" +
            "<if test='maxPrice != null'> AND i.price &lt;= #{maxPrice} </if>" +
            "ORDER BY " +
            "<choose>" +
            "  <when test='sort == \"priceAsc\"'>i.price ASC</when>" +
            "  <when test='sort == \"priceDesc\"'>i.price DESC</when>" +
            "  <when test='sort == \"hot\"'>commentCount DESC, i.create_time DESC</when>" +
            "  <otherwise>i.create_time DESC</otherwise>" +
            "</choose>" +
            "</script>")
    IPage<Item> selectPageForFront(Page<Item> page,
                                   @Param("categoryId") Long categoryId,
                                   @Param("keyword") String keyword,
                                   @Param("minPrice") java.math.BigDecimal minPrice,
                                   @Param("maxPrice") java.math.BigDecimal maxPrice,
                                   @Param("sort") String sort);

    /** 详情页（含卖家与分类信息，不限状态，用于展示"已售出"） */
    @Select("SELECT i.*, c.name AS categoryName, u.nickname AS sellerName, u.avatar AS sellerAvatar, " +
            "      (SELECT COUNT(*) FROM comment cm WHERE cm.item_id = i.id) AS commentCount " +
            "FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE i.deleted = 0 AND i.id = #{id}")
    Item selectDetailById(@Param("id") Long id);

    /** 我发布的商品 */
    @Select("<script>" +
            "SELECT i.*, c.name AS categoryName FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "WHERE i.deleted = 0 AND i.user_id = #{userId} " +
            "<if test='auditStatus != null'> AND i.audit_status = #{auditStatus} </if>" +
            "ORDER BY i.create_time DESC" +
            "</script>")
    List<Item> selectByUserId(@Param("userId") Long userId, @Param("auditStatus") Integer auditStatus);

    /** 后台待审核列表 */
    @Select("SELECT i.*, c.name AS categoryName, u.nickname AS sellerName FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE i.deleted = 0 AND i.audit_status = #{auditStatus} ORDER BY i.create_time DESC")
    List<Item> selectByAuditStatus(@Param("auditStatus") Integer auditStatus);

    /** 全部分类下商品数量（ECharts 分类分布） */
    @Select("SELECT c.name AS name, COUNT(i.id) AS value FROM category c " +
            "LEFT JOIN item i ON i.category_id = c.id AND i.deleted = 0 " +
            "WHERE c.deleted = 0 AND c.parent_id > 0 GROUP BY c.id, c.name " +
            "ORDER BY value DESC")
    List<Map<String, Object>> countByCategory();

    /** 按一级分类聚合的商品数量（看板圆环图用，二级分类向上归并到一级） */
    @Select("SELECT t.name AS name, COUNT(*) AS value FROM ( " +
            "  SELECT COALESCE(p.name, c.name) AS name FROM item i " +
            "  LEFT JOIN category c ON c.id = i.category_id " +
            "  LEFT JOIN category p ON p.id = c.parent_id AND p.parent_id = 0 " +
            "  WHERE i.deleted = 0 " +
            ") t GROUP BY t.name ORDER BY value DESC")
    List<Map<String, Object>> countByTopCategory();

    /** 猜你喜欢：同分类推荐（排除自己） */
    @Select("SELECT i.* FROM item i WHERE i.deleted = 0 AND i.audit_status = 1 " +
            "AND i.status = 1 AND i.deal_status = 1 AND i.category_id = #{categoryId} " +
            "AND i.id <> #{excludeId} ORDER BY i.create_time DESC LIMIT #{limit}")
    List<Item> selectRelated(@Param("categoryId") Long categoryId,
                             @Param("excludeId") Long excludeId,
                             @Param("limit") int limit);

    /**
     * 乐观锁式的"抢占成交"：只有满足 在售+未成交 才更新成功，
     * 返回受影响行数，用于防止同一件二手商品被两个买家同时下单。
     */
    @Update("UPDATE item SET deal_status = 2, status = 2, update_time = NOW() " +
            "WHERE id = #{id} AND deal_status = 1 AND status = 1 AND deleted = 0")
    int lockForSale(@Param("id") Long id);

    /** 取消订单时恢复商品为可售 */
    @Update("UPDATE item SET deal_status = 1, status = 1, update_time = NOW() " +
            "WHERE id = #{id} AND deal_status = 2 AND deleted = 0")
    int releaseForSale(@Param("id") Long id);

    /** 热门关键词 / 搜索热词统计所需的标题 */
    @Select("SELECT title FROM item WHERE deleted = 0 AND audit_status = 1 ORDER BY create_time DESC LIMIT #{limit}")
    List<String> selectRecentTitles(@Param("limit") int limit);

    /** 首页热门：按浏览量（浏览记录数）与上架时间排序 */
    @Select("SELECT i.*, c.name AS categoryName, u.nickname AS sellerName, " +
            "  (SELECT COUNT(*) FROM user_browse b WHERE b.item_id = i.id) AS viewCount " +
            "FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE i.deleted = 0 AND i.audit_status = 1 AND i.status = 1 " +
            "ORDER BY viewCount DESC, i.create_time DESC LIMIT #{limit}")
    List<Item> selectHot(@Param("limit") int limit);

    /** 首页最新上架 */
    @Select("SELECT i.*, c.name AS categoryName, u.nickname AS sellerName FROM item i " +
            "LEFT JOIN category c ON c.id = i.category_id " +
            "LEFT JOIN `user` u ON u.id = i.user_id " +
            "WHERE i.deleted = 0 AND i.audit_status = 1 AND i.status = 1 AND i.deal_status = 1 " +
            "ORDER BY i.create_time DESC LIMIT #{limit}")
    List<Item> selectNewest(@Param("limit") int limit);
}