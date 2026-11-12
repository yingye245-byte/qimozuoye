package com.campus.shop.config;

import com.campus.shop.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 所有页面共用的全局模型数据（模板里可直接用 ${loginUser}）
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("loginUser")
    public User loginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            User u = new User();
            u.setId(lu.getId());
            u.setUsername(lu.getUsername());
            u.setNickname(lu.getNickname());
            u.setAvatar(lu.getUser().getAvatar());
            u.setRole(lu.getRole());
            return u;
        }
        return null;
    }

    /**
     * 全局一次性提示：登录成功后写进 Session 的 loginMsg，
     * 在这里取出并放进 Model（取完即删），保证「登录成功」提示在所有页面都能显示一次。
     */
    @ModelAttribute("pendingMsg")
    public String pendingMsg(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object msg = session.getAttribute("loginMsg");
        if (msg != null) {
            session.removeAttribute("loginMsg");
        }
        return msg == null ? null : msg.toString();
    }
}