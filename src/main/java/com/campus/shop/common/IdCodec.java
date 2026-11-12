package com.campus.shop.common;

/**
 * 安全工具：把 ID 转成长度固定的雪花式字符串，避免 URL 遍历（IDOR）
 * 这里用「乘数 + 异或」的轻量混淆，够用且无需额外依赖
 */
public final class IdCodec {

    private static final long MULTI = 836_921_473_621L;
    private static final long MASK = 0x7FFF_FFFF_FFFFL;

    private IdCodec() {}

    public static String encode(Long id) {
        if (id == null) return "";
        long v = id * MULTI;
        v = v ^ 0x5A5A_5A5A_5A5AL;
        return Long.toString(v & MASK, 36);
    }

    public static Long decode(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            long v = Long.parseLong(code, 36);
            v = v ^ 0x5A5A_5A5A_5A5AL;
            long id = v / MULTI;
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }
}