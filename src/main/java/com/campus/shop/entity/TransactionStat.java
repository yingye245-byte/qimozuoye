package com.campus.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易统计（每日成交汇总，供 ECharts 看板与 Spring Task 自动汇总）
 */
@Data
@TableName("transaction_stat")
public class TransactionStat implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Integer dealCount;

    private BigDecimal dealAmount;
}