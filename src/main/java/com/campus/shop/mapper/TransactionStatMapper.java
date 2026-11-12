package com.campus.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.shop.entity.TransactionStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TransactionStatMapper extends BaseMapper<TransactionStat> {

    @Select("SELECT * FROM transaction_stat ORDER BY stat_date DESC LIMIT #{limit}")
    List<TransactionStat> selectRecent(@Param("limit") int limit);

    /** 幂等写入当日统计（定时任务每天跑一次，重复执行不会产生脏数据） */
    @Insert("INSERT INTO transaction_stat(stat_date, deal_count, deal_amount) VALUES(#{date}, #{count}, #{amount}) " +
            "ON DUPLICATE KEY UPDATE deal_count = VALUES(deal_count), deal_amount = VALUES(deal_amount)")
    int upsertStat(@Param("date") LocalDate date,
                   @Param("count") int count,
                   @Param("amount") java.math.BigDecimal amount);

    /** 当日实际成交数据 */
    @Select("SELECT COUNT(*) AS cnt, IFNULL(SUM(amount),0) AS amt FROM orders " +
            "WHERE status = 3 AND DATE(finish_time) = #{date}")
    java.util.Map<String, Object> sumDealByDate(@Param("date") LocalDate date);
}