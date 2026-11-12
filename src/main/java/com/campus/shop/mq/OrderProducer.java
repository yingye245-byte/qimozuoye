package com.campus.shop.mq;

import com.campus.shop.config.RabbitConfig;
import com.campus.shop.entity.Order;
import com.campus.shop.entity.User;
import com.campus.shop.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 消息生产者：集中管理三个场景的投递
 * <p>注意：所有发送都 try-catch 吞掉异常，
 * <b>MQ 挂掉不能影响主流程下单</b>（可靠性设计：数据库为准，MQ 失败由 Spring Task 兜底）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final RabbitTemplate rabbitTemplate;
    private final UserMapper userMapper;

    /**
     * 场景一：投递延迟消息，30 分钟后如果还没支付就自动取消
     */
    public void sendDelayCancel(Order order) {
        try {
            OrderMessage msg = buildMessage(order, OrderMessage.TYPE_CREATED);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.ORDER_DELAY_EXCHANGE,
                    RabbitConfig.ORDER_DELAY_KEY,
                    msg);
            log.info("[MQ] 已投递延迟取消消息 orderNo={} 将在30分钟后检查", order.getOrderNo());
        } catch (AmqpException e) {
            log.error("[MQ] 延迟消息投递失败 orderNo={}，将由定时任务兜底", order.getOrderNo(), e);
        }
    }

    /**
     * 场景二：异步发送下单成功邮件
     */
    public void sendMail(Order order) {
        try {
            OrderMessage msg = buildMessage(order, OrderMessage.TYPE_CREATED);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.MAIL_EXCHANGE,
                    RabbitConfig.MAIL_KEY,
                    msg);
            log.info("[MQ] 已投递邮件消息 orderNo={} to={}", order.getOrderNo(), msg.getBuyerEmail());
        } catch (AmqpException e) {
            log.error("[MQ] 邮件消息投递失败 orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 场景三：订单状态变更推送（支付、确认、完成、取消）
     */
    public void sendStatusNotify(Order order, String type) {
        try {
            OrderMessage msg = buildMessage(order, type);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.NOTIFY_EXCHANGE,
                    RabbitConfig.NOTIFY_KEY,
                    msg);
            log.info("[MQ] 已投递状态通知 type={} orderNo={}", type, order.getOrderNo());
        } catch (AmqpException e) {
            log.error("[MQ] 状态通知投递失败 type={} orderNo={}", type, order.getOrderNo(), e);
        }
    }

    /* ------------------ 私有 ------------------ */

    private OrderMessage buildMessage(Order order, String type) {
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(order.getId());
        msg.setOrderNo(order.getOrderNo());
        msg.setItemId(order.getItemId());
        msg.setItemTitle(order.getItemTitle());
        msg.setBuyerId(order.getBuyerId());
        msg.setBuyerName(order.getBuyerName());
        msg.setBuyerEmail(resolveEmail(order.getBuyerId(), order.getBuyerEmail()));
        msg.setSellerId(order.getSellerId());
        msg.setSellerName(order.getSellerName());
        msg.setSellerEmail(resolveEmail(order.getSellerId(), order.getSellerEmail()));
        msg.setAmount(order.getAmount());
        msg.setStatus(order.getStatus());
        msg.setType(type);
        msg.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return msg;
    }

    /** 订单查询结果里可能没带邮箱，兜底再查一次 */
    private String resolveEmail(Long userId, String fallback) {
        if (fallback != null && !fallback.isBlank()) return fallback;
        if (userId == null) return null;
        try {
            User u = userMapper.selectById(userId);
            return u == null ? null : u.getEmail();
        } catch (Exception e) {
            return null;
        }
    }
}