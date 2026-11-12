package com.campus.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.shop.entity.Order;

import java.util.List;
import java.util.Map;

/**
 * 订单服务
 */
public interface OrderService {

    /** 下单（乐观锁抢占商品 + 发 MQ 延迟消息 + 异步邮件） */
    Order create(Long itemId, Long buyerId);

    /** 支付（模拟，实际接支付网关） */
    void pay(Long orderId, Long buyerId);

    /** 卖家确认 */
    void confirm(Long orderId, Long sellerId);

    /** 买家确认收货，订单完成 */
    void finish(Long orderId, Long buyerId);

    /** 取消订单 */
    void cancel(Long orderId, Long userId);

    /** 我买到的 */
    IPage<Order> pageBuyer(Long userId, Integer status, int page, int size);

    /** 我卖出的 */
    IPage<Order> pageSeller(Long userId, Integer status, int page, int size);

    /** 后台全部订单 */
    /** 管理员强制取消订单（违规处理） */
    void adminCancel(Long orderId, String reason);

    IPage<Order> pageAdmin(Integer status, int page, int size);

    /** 订单详情（权限校验：只有买卖双方或管理员能看） */
    Order getDetail(Long orderId, Long userId, boolean isAdmin);

    /** 卖家经营看板数据 */
    Map<String, Object> sellerStat(Long userId);

    /** 待支付订单剩余秒数（用于页面倒计时） */
    long remainingSeconds(Order order);

    /** 未支付超时剩余秒数 */
    long remainSeconds(Order order);
}