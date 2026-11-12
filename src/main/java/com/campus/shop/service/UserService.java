package com.campus.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.shop.dto.RegisterDTO;
import com.campus.shop.entity.User;

import java.util.List;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    /** 注册（密码 BCrypt 加密，用户名唯一校验） */
    Long register(RegisterDTO dto);

    /** 登录专用查询：返回带 password 的对象 */
    User findByUsernameForLogin(String username);

    /** 修改个人资料 */
    void updateProfile(Long userId, String nickname, String email, String avatar);

    /** 修改密码 */
    void changePassword(Long userId, String oldPwd, String newPwd);

    /** 管理员：启用/禁用用户 */
    void toggleStatus(Long userId);

    /** 管理员：把普通用户升级为认证卖家 */
    void promoteToSeller(Long userId);

    /** 用户列表（后台） */
    List<User> listAll();

    /** 各角色人数统计 */
    List<java.util.Map<String, Object>> countByRole();
}