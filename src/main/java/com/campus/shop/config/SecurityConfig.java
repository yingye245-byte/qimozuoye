package com.campus.shop.config;

import com.campus.shop.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 配置（任务书第 7 章考点）
 *
 * <p>三级角色权限矩阵：</p>
 * <ul>
 *   <li>匿名可访问：/、/index、/login、/register、/item/list、/item/detail/**、静态资源</li>
 *   <li>普通用户及以上：/item/publish、/order/**、/profile/**</li>
 *   <li>认证卖家及以上：/seller/**</li>
 *   <li>仅管理员：/admin/**</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 懒加载注入：UserServiceImpl 依赖 PasswordEncoder，而 PasswordEncoder 由本类提供，
     * 直接构造注入会形成循环依赖，用 @Lazy 打破循环（Spring Boot 3 默认禁止循环引用）。
     */
    @Lazy
    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF：Thymeleaf 表单默认带 _csrf 隐藏域，保留防护（安全漏洞考点）
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index", "/login", "/register",
                        "/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/favicon.ico", "/error", "/403", "/h2-console/**").permitAll()
                // 注意：/uploads/** 已在上面放行（登录用户上传的图片必须能匿名浏览），
                // 真正的上传接口是 /upload/image，它需要登录且不在这里放行
                .requestMatchers("/item/list", "/item/detail/**", "/item/search").permitAll()
                .requestMatchers("/seller/**").hasAnyRole("SELLER", "ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new LoginSuccessHandler())
                .failureUrl("/login?error")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            .rememberMe(rm -> rm
                .key("campus-secondhand-remember-me")
                .tokenValiditySeconds(7 * 24 * 3600)
                .userDetailsService(userDetailsService)
            )

            .sessionManagement(sm -> sm
                .maximumSessions(1)
                .expiredUrl("/login?expired")
                .maxSessionsPreventsLogin(false)
            )

            .exceptionHandling(ex -> ex.accessDeniedPage("/403"))

            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}