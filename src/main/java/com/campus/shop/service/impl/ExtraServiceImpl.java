package com.campus.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.shop.common.BizException;
import com.campus.shop.common.CacheKeys;
import com.campus.shop.entity.*;
import com.campus.shop.mapper.*;
import com.campus.shop.service.CacheService;
import com.campus.shop.service.ExtraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtraServiceImpl implements ExtraService {

    private final CartMapper cartMapper;
    private final CommentMapper commentMapper;
    private final UserBrowseMapper userBrowseMapper;
    private final ItemMapper itemMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final TransactionStatMapper transactionStatMapper;
    private final CacheService cacheService;

    /* ==================== 购物车 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCart(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) throw new BizException("商品不存在");
        if (item.getUserId().equals(userId)) throw new BizException("不能收藏自己发布的商品");
        Long exists = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId).eq(Cart::getItemId, itemId));
        if (exists != null && exists > 0) {
            throw new BizException("该商品已在你的想要清单中");
        }
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setItemId(itemId);
        cartMapper.insert(cart);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCart(Long userId, Long cartId) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUserId, userId));  // 带 userId 条件，防止越权删别人的
    }

    @Override
    public List<Cart> listCart(Long userId) {
        return cartMapper.selectByUserId(userId);
    }

    @Override
    public int countCart(Long userId) {
        return cartMapper.countByUserId(userId);
    }

    /* ==================== 留言 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long itemId, Long fromUserId, String content) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) throw new BizException("商品不存在");
        if (item.getUserId().equals(fromUserId)) throw new BizException("不能给自己的商品留言");
        if (content == null || content.isBlank()) throw new BizException("留言内容不能为空");
        if (content.length() > 500) throw new BizException("留言最多 500 字");

        Comment c = new Comment();
        c.setItemId(itemId);
        c.setFromUser(fromUserId);
        c.setToUser(item.getUserId());
        c.setContent(content.trim());
        commentMapper.insert(c);
        // 留言数变化，商品详情缓存要失效
        cacheService.delete(CacheKeys.ITEM_DETAIL + itemId);
        log.info("[留言] itemId={} from={}", itemId, fromUserId);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyComment(Long commentId, Long sellerId, String reply) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) throw new BizException("留言不存在");
        if (!c.getToUser().equals(sellerId)) throw new BizException(403, "只有卖家可以回复");
        if (reply == null || reply.isBlank()) throw new BizException("回复内容不能为空");
        c.setReplyContent(reply.trim());
        commentMapper.updateById(c);
        cacheService.delete(CacheKeys.ITEM_DETAIL + c.getItemId());
    }

    @Override
    public List<Comment> listComments(Long itemId) {
        return commentMapper.selectByItemId(itemId);
    }

    @Override
    public List<Comment> listSellerComments(Long sellerId) {
        return commentMapper.selectByToUser(sellerId);
    }

    /* ==================== 浏览记录 ==================== */

    @Override
    public void recordBrowse(Long userId, Long itemId) {
        if (userId == null || itemId == null) return;
        try {
            UserBrowse ub = new UserBrowse();
            ub.setUserId(userId);
            ub.setItemId(itemId);
            userBrowseMapper.insert(ub);
            // 同时写 Redis 列表，页面直接用 Redis 展示最近浏览
            cacheService.pushBrowse(userId, itemId);
        } catch (Exception e) {
            log.warn("[浏览记录] 写入失败 userId={} itemId={}", userId, itemId);
        }
    }

    @Override
    public List<UserBrowse> listRecentBrowse(Long userId, int limit) {
        return userBrowseMapper.selectRecent(userId, limit);
    }

    /* ==================== 看板 ==================== */

    /**
     * 管理员数据看板：走 Redis 缓存（5 分钟），避免每次打开后台都全表统计
     */
    @Override
    public Map<String, Object> adminDashboard() {
        String key = CacheKeys.DASHBOARD + "admin";
        Map<String, Object> cached = cacheService.get(key, Map.class);
        if (cached != null) return cached;

        Map<String, Object> data = new HashMap<>();
        data.put("userTotal", userMapper.selectCount(null));
        data.put("itemTotal", itemMapper.selectCount(null));
        data.put("orderTotal", orderMapper.selectCount(null));
        data.put("categoryDistribution", categoryDistribution());
        data.put("dealTrend", dealTrend(7));
        data.put("orderStatusDistribution", orderStatusDistribution());
        data.put("sellerRank", sellerRank(5));
        data.put("roleDistribution", userMapper.countByRole());
        cacheService.set(key, data, CacheKeys.DASHBOARD_TTL);
        return data;
    }

    @Override
    public List<Map<String, Object>> categoryDistribution() {
        // 按「一级分类」聚合：商品挂在二级分类下（手机/笔记本…），看板直接展示二级会是很多零碎小项，
        // 这里用 parent_id 把二级归并到一级（数码/教材/生活用品/交通工具/运动户外）。
        return itemMapper.countByTopCategory();
    }

    @Override
    public List<Map<String, Object>> dealTrend(int days) {
        return orderMapper.selectDealTrend(days);
    }

    @Override
    public List<Map<String, Object>> orderStatusDistribution() {
        return orderMapper.countByStatus();
    }

    @Override
    public List<Map<String, Object>> sellerRank(int limit) {
        return userMapper.selectSellerRank(limit);
    }
}