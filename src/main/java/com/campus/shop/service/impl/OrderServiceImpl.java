package com.campus.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.shop.common.BizException;
import com.campus.shop.common.Constants;
import com.campus.shop.common.IdCodec;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.Order;
import com.campus.shop.mapper.ItemMapper;
import com.campus.shop.mapper.OrderMapper;
import com.campus.shop.mq.OrderMessage;
import com.campus.shop.mq.OrderProducer;
import com.campus.shop.service.ItemService;
import com.campus.shop.service.OrderService;
import com.campus.shop.service.OrderStateService;
import com.campus.shop.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final ItemService itemService;
    private final OrderStateService orderStateService;
    private final OrderProducer orderProducer;

    /**
     * 下单核心流程（任务书高阶级重点：事务 + 并发 + 消息）
     *
     * <pre>
     * 1. 校验商品状态（在售/未成交/不是自己的）
     * 2. 乐观抢占：UPDATE ... WHERE deal_status=1 只有一行受影响才算成功
     *    —— 二手商品是唯一的，防止两个人同时下单买到同一件
     * 3. 写订单（初始状态：待支付）
     * 4. 事务提交后（TransactionSynchronization）再发 MQ：
     *    延迟取消消息 + 异步邮件
     *    —— 必须等事务提交后再发，否则消费者可能读到还没落库的订单
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order create(Long itemId, Long buyerId) {
        Item item = itemMapper.selectDetailById(itemId);
        if (item == null) {
            throw new BizException("商品不存在或已被删除");
        }
        if (item.getUserId().equals(buyerId)) {
            throw new BizException("不能购买自己发布的商品");
        }
        if (!item.isOnSale()) {
            throw new BizException("该商品已售出或已下架");
        }

        // 乐观抢占商品（并发安全关键点）
        int locked = itemMapper.lockForSale(itemId);
        if (locked == 0) {
            throw new BizException("手慢啦，该商品刚刚被别人拍下");
        }

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.next());
        order.setItemId(itemId);
        order.setBuyerId(buyerId);
        order.setSellerId(item.getUserId());
        order.setAmount(item.getPrice());
        order.setStatus(Order.STATUS_UNPAID);
        orderMapper.insert(order);

        // 补齐展示字段，供 MQ 消息与后续通知使用
        order.setItemTitle(item.getTitle());
        order.setItemCover(item.getCover());

        Long orderId = order.getId();
        // 事务提交后再发消息，避免消费者读到未提交数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Order fresh = orderMapper.selectDetailById(orderId);
                Order payload = fresh != null ? fresh : order;
                orderProducer.sendDelayCancel(payload);   // 场景一
                orderProducer.sendMail(payload);          // 场景二
            }
        });

        log.info("[下单] 成功 orderNo={} itemId={} buyerId={} amount={}",
                order.getOrderNo(), itemId, buyerId, order.getAmount());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId, Long buyerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getBuyerId().equals(buyerId)) throw new BizException(403, "无权操作该订单");
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())) {
            throw new BizException("订单当前状态无法支付：" + order.getStatusName());
        }
        if (orderStateService.isTimeout(order)) {
            // 已经超时但消息还没到，就地取消
            orderStateService.cancelOrder(orderId, "超时未支付");
            throw new BizException("订单已超时取消，请重新下单");
        }

        order.setStatus(Order.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("[支付] 订单已支付 orderNo={}", order.getOrderNo());

        notifyAfterCommit(orderId, OrderMessage.TYPE_PAID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, Long sellerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getSellerId().equals(sellerId)) throw new BizException(403, "无权操作该订单");
        if (!Integer.valueOf(Order.STATUS_PAID).equals(order.getStatus())) {
            throw new BizException("只有已支付的订单可以确认");
        }
        order.setStatus(Order.STATUS_CONFIRM);
        orderMapper.updateById(order);
        log.info("[确认] 卖家已确认订单 orderNo={}", order.getOrderNo());

        notifyAfterCommit(orderId, OrderMessage.TYPE_CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long orderId, Long buyerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getBuyerId().equals(buyerId)) throw new BizException(403, "无权操作该订单");
        if (!Integer.valueOf(Order.STATUS_CONFIRM).equals(order.getStatus())
                && !Integer.valueOf(Order.STATUS_PAID).equals(order.getStatus())) {
            throw new BizException("订单当前状态无法确认收货");
        }
        order.setStatus(Order.STATUS_FINISH);
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("[完成] 订单已完成 orderNo={}", order.getOrderNo());

        notifyAfterCommit(orderId, OrderMessage.TYPE_FINISHED);
    }

    @Override
    public void cancel(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) throw new BizException(403, "无权操作该订单");
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())) {
            throw new BizException("订单当前状态（" + order.getStatusName() + "）无法取消，如需取消请联系管理员");
        }
        boolean done = orderStateService.cancelOrder(orderId, isBuyer ? "买家主动取消" : "卖家取消订单");
        if (!done) {
            throw new BizException("订单取消失败，可能已被其他操作处理，请刷新后重试");
        }
    }

    @Override
    public void adminCancel(Long orderId, String reason) {
        orderStateService.adminCancel(orderId, reason);
    }

    @Override
    public IPage<Order> pageBuyer(Long userId, Integer status, int page, int size) {
        return orderMapper.selectBuyerPage(new Page<>(page, size), userId, status);
    }

    @Override
    public IPage<Order> pageSeller(Long userId, Integer status, int page, int size) {
        return orderMapper.selectSellerPage(new Page<>(page, size), userId, status);
    }

    @Override
    public IPage<Order> pageAdmin(Integer status, int page, int size) {
        return orderMapper.selectAdminPage(new Page<>(page, size), status);
    }

    @Override
    public Order getDetail(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderMapper.selectDetailById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!isAdmin && !order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BizException(403, "无权查看该订单");
        }
        return order;
    }

    @Override
    public Map<String, Object> sellerStat(Long userId) {
        return orderMapper.selectSellerStat(userId);
    }

    @Override
    public long remainingSeconds(Order order) {
        if (order == null || !Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus())
                || order.getCreateTime() == null) {
            return 0;
        }
        LocalDateTime deadline = order.getCreateTime().plusMinutes(Constants.ORDER_TIMEOUT_MINUTES);
        long sec = Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(sec, 0);
    }

    @Override
    public long remainSeconds(Order order) {
        return remainingSeconds(order);
    }

    /** 事务提交后投递状态通知，保证消费者能读到最新状态 */
    private void notifyAfterCommit(Long orderId, String type) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Order fresh = orderMapper.selectDetailById(orderId);
                if (fresh != null) {
                    orderProducer.sendStatusNotify(fresh, type);
                }
            }
        });
    }
}