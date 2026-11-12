package com.campus.shop.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;

/**
 * 登录成功处理器：按角色跳到对应工作台，并用 Session 保存欢迎提示。
 *
 * <p>说明：Security 的 defaultSuccessUrl 会丢弃 RedirectAttributes 里的 flash 消息，
 * 因此这里把提示放进 Session，由 UserController 的 pendingMsg() 取出来显示。</p>
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public LoginSuccessHandler() {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String target = "/";
        for (GrantedAuthority ga : authorities) {
            String a = ga.getAuthority();
            if ("ROLE_ADMIN".equals(a)) {
                target = "/admin/dashboard";
            } else if ("ROLE_SELLER".equals(a)) {
                target = "/seller";
            } else {
                target = "/";
            }
            if ("ROLE_ADMIN".equals(a)) {
                break;
            }
        }
        request.getSession(true).setAttribute("loginMsg", "登录成功，欢迎回来 " + authentication.getName() + "！");
        setDefaultTargetUrl(target);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}