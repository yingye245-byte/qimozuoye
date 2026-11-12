package com.campus.shop.config;

import com.campus.shop.common.Constants;
import com.campus.shop.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（Spring Security）
 * <p>实现 UserDetails 接口，把数据库里的 user 包装成 Security 认识的对象。</p>
 * <p>角色映射：1→ROLE_USER  2→ROLE_SELLER  3→ROLE_ADMIN</p>
 */
@Getter
public class LoginUser implements UserDetails {

    private final User user;

    public LoginUser(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        int role = user.getRole() == null ? Constants.ROLE_USER : user.getRole();
        String roleName = switch (role) {
            case Constants.ROLE_ADMIN -> Constants.ROLE_NAME_ADMIN;
            case Constants.ROLE_SELLER -> Constants.ROLE_NAME_SELLER;
            default -> Constants.ROLE_NAME_USER;
        };
        // 认证卖家同时拥有普通用户权限；管理员同时拥有全部权限
        List<GrantedAuthority> list = new java.util.ArrayList<>();
        list.add(new SimpleGrantedAuthority(roleName));
        if (role >= Constants.ROLE_SELLER) {
            list.add(new SimpleGrantedAuthority(Constants.ROLE_NAME_USER));
        }
        if (role >= Constants.ROLE_ADMIN) {
            list.add(new SimpleGrantedAuthority(Constants.ROLE_NAME_SELLER));
        }
        return list;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 状态 0 = 禁用，登录直接拒绝 */
    @Override
    public boolean isEnabled() {
        return Integer.valueOf(Constants.USER_NORMAL).equals(user.getStatus());
    }

    public Long getId() {
        return user.getId();
    }

    public Integer getRole() {
        return user.getRole();
    }

    public String getNickname() {
        return user.getNickname() == null ? user.getUsername() : user.getNickname();
    }
}