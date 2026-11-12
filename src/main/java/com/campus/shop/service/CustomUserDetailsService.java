package com.campus.shop.service;

import com.campus.shop.config.LoginUser;
import com.campus.shop.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户加载（任务书第7章：自定义认证）
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 注意：这里必须用带 password 的查询
        User user = userService.findByUsernameForLogin(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return new LoginUser(user);
    }
}