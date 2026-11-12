package com.campus.shop.service;

/**
 * 订单状态流转服务（供 MQ 消费者、Spring Task、Controller 三方调用，统一收口）
 */
import com.campus.shop.common.BizException;
import com.campus.shop.common.Constants;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.Order;
import com.campus.shop.mapper.ItemMapper;
import com.campus.shop.mapper.OrderMapper;
import com.campus.shop.mq.OrderMessage;
import com.campus.shop.mq.OrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateService {

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final OrderProducer orderProducer;

    /**
     * 超时/主动取消订单：把订单置为已取消，并恢复商品为可售。
     * <p>幂等：只有 status=0（待支付）才执行，重复消费同一条消息不会出错。</p>
     *
     * @return true 表示本次真的取消了；false 表示订单已支付/已完成，无需处理
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[取消订单] 订单不存在 id={}", orderId);
            return false;
        }
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())) {
            log.info("[取消订单] 订单当前状态={}，无需取消 orderNo={}", order.getStatus(), order.getOrderNo());
            return false;
        }

        order.setStatus(Order.STATUS_CANCEL);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(reason);
        int rows = orderMapper.updateById(order);
        if (rows == 0) {
            return false;
        }

        // 恢复商品：deal_status 2 -> 1，status 2 -> 1
        itemMapper.releaseForSale(order.getItemId());
        log.info("[取消订单] 成功 orderNo={} 原因={}", order.getOrderNo(), reason);

        // 异步推送取消通知
        fillForNotify(order);
        orderProducer.sendStatusNotify(order, OrderMessage.TYPE_CANCELED);
        return true;
    }

    /** 支付超时判定：创建时间 + 30 分钟 < 现在 */
    public boolean isTimeout(Order order) {
        if (order.getCreateTime() == null) return false;
        return order.getCreateTime()
                .plusMinutes(Constants.ORDER_TIMEOUT_MINUTES)
                .isBefore(LocalDateTime.now());
    }

    /** 补齐展示字段，保证通知里能拿到商品标题与双方邮箱 */
    private void fillForNotify(Order order) {
        if (order.getItemTitle() == null) {
            Item item = itemMapper.selectById(order.getItemId());
            if (item != null) {
                order.setItemTitle(item.getTitle());
            }
        }
    }

    /** 买家主动取消（仅待支付状态允许） */
    @Transactional(rollbackFor = Exception.class)
    public void buyerCancel(Long orderId, Long buyerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BizException(403, "无权操作该订单");
        }
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())) {
            throw new BizException("只有待支付订单可以取消");
        }
        cancelOrder(orderId, "买家主动取消");
    }

    /**
     * 管理员强制取消：待支付 / 已支付 / 已确认 的订单都允许取消（违规处理），
     * 已完成和已取消的订单不允许再操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean adminCancel(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        Integer st = order.getStatus();
        if (Integer.valueOf(Order.STATUS_FINISH).equals(st)
                || Integer.valueOf(Order.STATUS_CANCEL).equals(st)
                || Integer.valueOf(Order.STATUS_CLOSED).equals(st)) {
            throw new BizException("该订单已结束，不能取消");
        }
        String realReason = "管理员取消：" + (reason == null || reason.isBlank() ? "违规商品处理" : reason);
        order.setStatus(Order.STATUS_CANCEL);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(realReason);
        if (orderMapper.updateById(order) == 0) {
            return false;
        }
        itemMapper.releaseForSale(order.getItemId());
        log.info("[管理员取消订单] 成功 orderNo={} 原状态={} 原因={}", order.getOrderNo(), st, realReason);
        fillForNotify(order);
        orderProducer.sendStatusNotify(order, OrderMessage.TYPE_CANCELED);
        return true;
    }
}