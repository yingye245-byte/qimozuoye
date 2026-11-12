package com.campus.shop.service;

import com.campus.shop.entity.Cart;
import com.campus.shop.entity.Comment;
import com.campus.shop.entity.UserBrowse;

import java.util.List;
import java.util.Map;

/**
 * 附加业务：购物车 / 留言 / 浏览记录 / 数据看板
 */
public interface ExtraService {

    /* ---------- 购物车（想要清单） ---------- */

    boolean addCart(Long userId, Long itemId);

    void removeCart(Long userId, Long items);

    List<Cart> listCart(Long userId);

    int countCart(Long userId);

    /* ---------- 留言 ---------- */

    Long addComment(Long itemId, Long fromUserId, String content);

    void replyComment(Long commentId, Long sellerId, String reply);

    List<Comment> listComments(Long itemId);

    List<Comment> listSellerComments(Long sellerId);

    /* ---------- 浏览记录 ---------- */

    void recordBrowse(Long userId, Long itemId);

    List<UserBrowse> listRecentBrowse(Long userId, int limit);

    /* ---------- 数据看板 ---------- */

    Map<String, Object> adminDashboard();

    List<Map<String, Object>> categoryDistribution();

    List<Map<String, Object>> dealTrend(int days);

    List<Map<String, Object>> orderStatusDistribution();

    List<Map<String, Object>> sellerRank(int limit);
}