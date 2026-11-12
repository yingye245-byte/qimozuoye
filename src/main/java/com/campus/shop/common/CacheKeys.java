package com.campus.shop.common;

/**
 * 缓存 Key 与 TTL 常量（Redis 统一出口，避免散落在各处易冲突）
 */
public final class CacheKeys {

    private CacheKeys() {}

    /** 分类树（低频变更，缓存 1 小时） */
    public static final String CATEGORY_TREE = "campus:category:tree";
    public static final long CATEGORY_TREE_TTL = 3600L;

    /** 商品详情缓存：campus:item:detail:{id}（30 分钟） */
    public static final String ITEM_DETAIL = "campus:item:detail:";
    public static final long ITEM_DETAIL_TTL = 1800L;

    /** 商品列表缓存：campus:item:list:{条件hash}（5 分钟） */
    public static final String ITEM_LIST = "campus:item:list:";
    public static final long ITEM_LIST_TTL = 300L;

    /** 首页热销（10 分钟） */
    public static final String HOT_ITEMS = "campus:item:hot";
    public static final long HOT_ITEMS_TTL = 600L;

    /** 空值缓存（防穿透，2 分钟） */
    public static final String NULL_HOLD = "campus:null:";
    public static final long NULL_HOLD_TTL = 120L;

    /** 互斥锁（防击穿），前缀 + 业务 key */
    public static final String LOCK = "campus:lock:";
    public static final long LOCK_TTL = 10L;

    /** 看板统计（5 分钟） */
    public static final String DASHBOARD = "campus:dashboard:";
    public static final long DASHBOARD_TTL = 300L;

    /** 用户单点登录 token 前缀 */
    public static final String TOKEN = "campus:token:";

    /** 搜索热词 ZSet */
    public static final String SEARCH_HOT = "campus:search:hot";

    /** 订单支付倒计时 */
    public static final String ORDER_COUNTDOWN = "campus:order:countdown:";

    /** 浏览记录（Redis List，最近 20 条） */
    public static final String BROWSE = "campus:browse:";
}