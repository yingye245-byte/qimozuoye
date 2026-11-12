package com.campus.shop.common;

/**
 * 业务常量
 */
public final class Constants {

    private Constants() {}

    /* -------- 角色 -------- */
    public static final int ROLE_USER = 1;
    public static final int ROLE_SELLER = 2;
    public static final int ROLE_ADMIN = 3;

    /* -------- 用户状态 -------- */
    public static final int USER_DISABLED = 0;
    public static final int USER_NORMAL = 1;

    /* -------- 审核状态 -------- */
    public static final int AUDIT_PENDING = 0;
    public static final int AUDIT_PASS = 1;
    public static final int AUDIT_REJECT = 2;

    /* -------- 商品状态 -------- */
    public static final int ITEM_ON_SALE = 1;
    public static final int ITEM_OFF_SALE = 2;

    /* -------- 成交状态 -------- */
    public static final int DEAL_NONE = 1;
    public static final int DEAL_DONE = 2;
    public static final int DEAL_CLOSED = 3;

    /** 订单支付超时（分钟），MQ 延迟消息与 Spring Task 都用它 */
    public static final int ORDER_TIMEOUT_MINUTES = 30;

    /** Security 角色前缀 */
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_NAME_USER = "ROLE_USER";
    public static final String ROLE_NAME_SELLER = "ROLE_SELLER";
    public static final String ROLE_NAME_ADMIN = "ROLE_ADMIN";
}