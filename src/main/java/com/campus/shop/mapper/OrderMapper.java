package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.shop.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    String BASE_SELECT =
            "SELECT o.*, i.title AS itemTitle, i.cover AS itemCover, " +
            "       bu.nickname AS buyerName, bu.email AS buyerEmail, " +
            "       su.nickname AS sellerName, su.email AS sellerEmail " +
            "FROM orders o " +
            "LEFT JOIN item i ON i.id = o.item_id " +
            "LEFT JOIN `user` bu ON bu.id = o.buyer_id " +
            "LEFT JOIN `user` su ON su.id = o.seller_id ";

    @Select(BASE_SELECT + "WHERE o.id = #{id}")
    Order selectDetailById(@Param("id") Long id);

    @Select(BASE_SELECT + "WHERE o.order_no = #{orderNo}")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /** 我买到的 */
    @Select("<script>" + BASE_SELECT +
            "WHERE o.buyer_id = #{userId} " +
            "<if test='status != null'> AND o.status = #{status} </if>" +
            " ORDER BY o.create_time DESC</script>")
    IPage<Order> selectBuyerPage(Page<Order> page, @Param("userId") Long userId, @Param("status") Integer status);

    /** 我卖出的 */
    @Select("<script>" + BASE_SELECT +
            "WHERE o.seller_id = #{userId} " +
            "<if test='status != null'> AND o.status = #{status} </if>" +
            " ORDER BY o.create_time DESC</script>")
    IPage<Order> selectSellerPage(Page<Order> page, @Param("userId") Long userId, @Param("status") Integer status);

    /** 后台全部订单 */
    @Select("<script>" + BASE_SELECT +
            "WHERE 1=1 " +
            "<if test='status != null'> AND o.status = #{status} </if>" +
            " ORDER BY o.create_time DESC</script>")
    IPage<Order> selectAdminPage(Page<Order> page, @Param("status") Integer status);

    /** 查出超时未支付订单（Spring Task 补偿 + MQ 延迟兜底） */
    @Select("SELECT * FROM orders WHERE status = 0 AND create_time < #{deadline}")
    List<Order> selectTimeoutUnpaid(@Param("deadline") LocalDateTime deadline);

    /** 近 N 天成交趋势（ECharts 折线图）
     * <p>按「成单时间」统计：优先用完成时间 finish_time，没写完成时间的退回支付时间 pay_time，
     * 这样昨天（或更早）完成的订单也能正确落到那一天，而不是全部堆到今天。</p> */
    @Select("SELECT DATE_FORMAT(COALESCE(finish_time, pay_time, create_time),'%Y-%m-%d') AS day, " +
            "COUNT(*) AS cnt, IFNULL(SUM(amount),0) AS amt " +
            "FROM orders WHERE status = 3 " +
            "AND COALESCE(finish_time, pay_time, create_time) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY day ORDER BY day")
    List<Map<String, Object>> selectDealTrend(@Param("days") int days);

    /** 各状态订单数量（ECharts 漏斗/圆环） */
    @Select("SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /** 卖家经营数据看板 */
    @Select("SELECT " +
            " (SELECT COUNT(*) FROM item WHERE user_id=#{uid} AND deleted=0) AS itemTotal, " +
            " (SELECT COUNT(*) FROM orders WHERE seller_id=#{uid}) AS orderTotal, " +
            " (SELECT COUNT(*) FROM orders WHERE seller_id=#{uid} AND status=3) AS finishTotal, " +
            " (SELECT IFNULL(SUM(amount),0) FROM orders WHERE seller_id=#{uid} AND status=3) AS totalAmount, " +
            " (SELECT COUNT(*) FROM comment WHERE to_user=#{uid}) AS commentTotal")
    Map<String, Object> selectSellerStat(@Param("uid") Long userId);
}