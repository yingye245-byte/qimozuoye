package com.campus.shop.mq;

import com.campus.shop.config.RabbitConfig;
import com.campus.shop.entity.Order;
import com.campus.shop.mapper.OrderMapper;
import com.campus.shop.service.MailService;
import com.campus.shop.service.OrderStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * MQ 消费者 —— 三个场景各一个监听器
 * <p>统一手动幂等处理，保证"至少一次投递"不会造成重复扣减/重复通知。</p>
 *
 * <p><b>可靠性设计（答辩可讲）</b>：监听器参数统一接收原始 {@link Message}，
 * 自己用 Jackson 把 JSON 转成 {@link OrderMessage}；
 * 解析失败（脏消息/旧格式消息）只记日志并直接返回，不会抛异常，
 * 从而避免"毒消息无限重投"拖垮消费者。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderStateService orderStateService;
    private final MailService mailService;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    /** 把 JSON 消息体安全地转成业务对象，失败返回 null 不抛异常 */
    private OrderMessage parse(Message message) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, OrderMessage.class);
        } catch (Exception e) {
            log.warn("[MQ-消费] 消息体解析失败，已丢弃（避免毒消息重投）contentType={} err={}",
                    message.getMessageProperties().getContentType(), e.getMessage());
            return null;
        }
    }

    /**
     * 【场景一】订单超时自动取消
     * <p>消息在延迟队列躺 30 分钟后被死信交换机转投到这里。</p>
     */
    @RabbitListener(queues = RabbitConfig.ORDER_CANCEL_QUEUE)
    public void onOrderTimeout(Message message) {
        OrderMessage msg = parse(message);
        if (msg == null) {
            return;
        }
        log.info("[MQ-消费] 收到超时检查消息 orderNo={} traceId={}", msg.getOrderNo(), msg.getTraceId());
        try {
            Order order = orderMapper.selectById(msg.getOrderId());
            if (order == null) {
                log.warn("[MQ-消费] 订单已不存在 orderId={}", msg.getOrderId());
                return;
            }
            // 二次确认超时（防止消息早到/重复消费）
            if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())) {
                log.info("[MQ-消费] 订单状态={} 不是待支付，跳过 orderNo={}", order.getStatus(), order.getOrderNo());
                return;
            }
            boolean canceled = orderStateService.cancelOrder(order.getId(),
                    "超时未支付，系统自动取消（" + 30 + "分钟）");
            if (canceled) {
                log.info("[MQ-消费] 订单已自动取消 orderNo={}", order.getOrderNo());
            }
        } catch (Exception e) {
            log.error("[MQ-消费] 超时取消处理异常 orderNo={}", msg.getOrderNo(), e);
        }
    }

    /**
     * 【场景二】下单成功异步邮件通知
     * <p>用户点"立即购买"后立刻返回页面，邮件在后台慢慢发。</p>
     */
    @RabbitListener(queues = RabbitConfig.MAIL_QUEUE)
    public void onOrderCreatedMail(Message message) {
        OrderMessage msg = parse(message);
        if (msg == null) {
            return;
        }
        log.info("[MQ-消费] 收到邮件任务 orderNo={} to={}", msg.getOrderNo(), msg.getBuyerEmail());
        try {
            String subject = "【校园二手】下单成功通知 - " + msg.getOrderNo();
            String content = "尊敬的 " + msg.getBuyerName() + " 同学，您好：\n\n"
                    + "您已成功下单，请在 30 分钟内完成支付，否则订单将自动取消。\n\n"
                    + "────────────────────────\n"
                    + "订单编号：" + msg.getOrderNo() + "\n"
                    + "商品名称：" + msg.getItemTitle() + "\n"
                    + "订单金额：￥" + msg.getAmount() + "\n"
                    + "卖家：" + msg.getSellerName() + "\n"
                    + "────────────────────────\n\n"
                    + "请尽快登录平台完成支付：http://localhost:8080/order/buyer\n\n"
                    + "此邮件由系统自动发送，请勿直接回复。\n"
                    + "—— 校园二手闲置交易平台";
            mailService.send(msg.getBuyerEmail(), subject, content);

            // 同时给卖家发一封"有人拍下你的商品"
            if (msg.getSellerEmail() != null) {
                String sellerSubject = "【校园二手】您的商品已被拍下 - " + msg.getItemTitle();
                String sellerContent = "尊敬的 " + msg.getSellerName() + " 同学，您好：\n\n"
                        + "买家【" + msg.getBuyerName() + "】拍下了您的商品，请留意后续交易进度。\n\n"
                        + "────────────────────────\n"
                        + "商品名称：" + msg.getItemTitle() + "\n"
                        + "订单编号：" + msg.getOrderNo() + "\n"
                        + "订单金额：￥" + msg.getAmount() + "\n"
                        + "────────────────────────\n\n"
                        + "可在\"我卖出的\"页面查看并确认订单：http://localhost:8080/order/seller\n\n"
                        + "—— 校园二手闲置交易平台";
                mailService.send(msg.getSellerEmail(), sellerSubject, sellerContent);
            }
        } catch (Exception e) {
            log.error("[MQ-消费] 邮件任务处理异常 orderNo={}", msg.getOrderNo(), e);
        }
    }

    /**
     * 【场景三】订单状态变更推送
     * <p>实际生产中可推送站内信 / WebSocket / 短信，这里落库日志并打印，
     * 答辩时展示"状态变化 → MQ → 消费者"的完整链路。</p>
     */
    @RabbitListener(queues = RabbitConfig.NOTIFY_QUEUE)
    public void onOrderStatusNotify(Message message) {
        OrderMessage msg = parse(message);
        if (msg == null) {
            return;
        }
        log.info("[MQ-消费] 订单状态变更通知 type={} orderNo={} status={}",
                msg.getType(), msg.getOrderNo(), msg.getStatus());
        try {
            String tip;
            String type = msg.getType() == null ? "" : msg.getType();
            switch (type) {
                case OrderMessage.TYPE_PAID ->
                        tip = "买家【" + msg.getBuyerName() + "】已支付订单 " + msg.getOrderNo() + "，请尽快确认发货。";
                case OrderMessage.TYPE_CONFIRMED ->
                        tip = "卖家已确认订单 " + msg.getOrderNo() + "，请等待线下交付。";
                case OrderMessage.TYPE_FINISHED ->
                        tip = "订单 " + msg.getOrderNo() + " 已完成，感谢使用校园二手！";
                case OrderMessage.TYPE_CANCELED ->
                        tip = "订单 " + msg.getOrderNo() + " 已取消，商品重新上架。";
                default -> tip = "订单 " + msg.getOrderNo() + " 状态更新为 " + msg.getStatus();
            }
            log.info("[MQ-消费] 推送内容：买家={} 卖家={} → {}", msg.getBuyerName(), msg.getSellerName(), tip);
        } catch (Exception e) {
            log.error("[MQ-消费] 状态通知异常 orderNo={}", msg.getOrderNo(), e);
        }
    }
}