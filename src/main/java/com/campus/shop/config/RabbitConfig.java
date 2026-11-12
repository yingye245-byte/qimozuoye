package com.campus.shop.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑声明（任务书高阶级考点：消息队列异步解耦）
 *
 * <p>本项目共设计 <b>三个真实业务场景</b>：</p>
 * <ol>
 *   <li><b>订单超时自动取消</b> —— 延迟队列（TTL + 死信），30 分钟未支付自动关单并恢复商品</li>
 *   <li><b>下单异步邮件通知</b> —— 下单后立刻返回，邮件在消费者里慢慢发，不阻塞用户</li>
 *   <li><b>订单状态变更推送</b> —— 支付/完成等状态变化异步推送给买卖双方</li>
 * </ol>
 */
@Configuration
public class RabbitConfig {

    /* ============ 场景一：订单超时取消（延迟队列） ============ */

    /** 业务交换机：订单 */
    public static final String ORDER_EXCHANGE = "campus.order.exchange";

    /** 延迟交换机：没有消费者，消息过期后被投递到死信交换机 */
    public static final String ORDER_DELAY_EXCHANGE = "campus.order.delay.exchange";

    /** 死信交换机：真正处理超时取消 */
    public static final String ORDER_DLX_EXCHANGE = "campus.order.dlx.exchange";

    public static final String ORDER_DELAY_QUEUE = "campus.order.delay.queue";
    public static final String ORDER_CANCEL_QUEUE = "campus.order.cancel.queue";

    public static final String ORDER_DELAY_KEY = "order.delay";
    public static final String ORDER_CANCEL_KEY = "order.cancel";

    /* ============ 场景二：异步邮件 ============ */

    public static final String MAIL_EXCHANGE = "campus.mail.exchange";
    public static final String MAIL_QUEUE = "campus.mail.queue";
    public static final String MAIL_KEY = "mail.send";

    /* ============ 场景三：订单状态推送 ============ */

    public static final String NOTIFY_EXCHANGE = "campus.notify.exchange";
    public static final String NOTIFY_QUEUE = "campus.notify.queue";
    public static final String NOTIFY_KEY = "order.notify";

    /* ------------------------- 声明 Bean ------------------------- */

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /** 延迟交换机同样用 Direct（生产环境可用 x-delayed-message 插件，此处用 TTL+DLX 方案免装插件） */
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 延迟队列：消息在这里"躺"30 分钟，没有消费者；
     * 过期后按 x-dead-letter-* 声明转投到死信队列。
     */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_KEY);
        // 队列级别兜底过期时间：30 分钟
        args.put("x-message-ttl", 30 * 60 * 1000);
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(ORDER_DELAY_KEY);
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue()).to(orderDlxExchange()).with(ORDER_CANCEL_KEY);
    }

    /* ---------- 邮件 ---------- */

    @Bean
    public DirectExchange mailExchange() {
        return new DirectExchange(MAIL_EXCHANGE, true, false);
    }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE).build();
    }

    @Bean
    public Binding mailBinding() {
        return BindingBuilder.bind(mailQueue()).to(mailExchange()).with(MAIL_KEY);
    }

    /* ---------- 状态推送 ---------- */

    @Bean
    public DirectExchange notifyExchange() {
        return new DirectExchange(NOTIFY_EXCHANGE, true, false);
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(notifyQueue()).to(notifyExchange()).with(NOTIFY_KEY);
    }

    /* ============ 消息转换器：JSON（解决 JDK 序列化白名单限制） ============ */

    /**
     * 用 JSON 而非 JDK 序列化传输消息体：
     * 1. 消息在 RabbitMQ 管理界面里可读，答辩演示直观；
     * 2. 避开 SimpleMessageConverter 的 allowed-list 反序列化白名单限制
     *    （否则消费端会抛 SecurityException: Attempt to deserialize unauthorized class）。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(om);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(4);
        return factory;
    }
}