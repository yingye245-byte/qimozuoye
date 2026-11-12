package com.campus.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.shop.common.BizException;
import com.campus.shop.common.Constants;
import com.campus.shop.dto.RegisterDTO;
import com.campus.shop.entity.User;
import com.campus.shop.mapper.UserMapper;
import com.campus.shop.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException("两次输入的密码不一致");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已被占用，请换一个");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        // 注册只允许选 普通用户 或 认证卖家，管理员角色不可自助注册
        int role = (dto.getRole() != null && dto.getRole() == Constants.ROLE_SELLER)
                ? Constants.ROLE_SELLER : Constants.ROLE_USER;
        user.setRole(role);
        user.setStatus(Constants.USER_NORMAL);
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("[注册] 新用户 {} 角色={}", user.getUsername(), role);
        return user.getId();
    }

    @Override
    public User findByUsernameForLogin(String username) {
        return userMapper.selectByUsernameForLogin(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, String nickname, String email, String avatar) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname.trim());
        if (email != null && !email.isBlank()) user.setEmail(email.trim());
        if (avatar != null && !avatar.isBlank()) user.setAvatar(avatar);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPwd, String newPwd) {
        if (newPwd == null || newPwd.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        User db = userMapper.selectByUsernameForLogin(
                userMapper.selectById(userId).getUsername());
        if (db == null || !passwordEncoder.matches(oldPwd, db.getPassword())) {
            throw new BizException("原密码不正确");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(update);
        log.info("[改密] 用户 {} 修改密码成功", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (Constants.ROLE_ADMIN == user.getRole()) {
            throw new BizException("不能禁用管理员账号");
        }
        user.setStatus(Constants.USER_NORMAL == user.getStatus()
                ? Constants.USER_DISABLED : Constants.USER_NORMAL);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promoteToSeller(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (user.getRole() == Constants.ROLE_ADMIN) throw new BizException("管理员无需升级");
        if (user.getRole() == Constants.ROLE_SELLER) throw new BizException("该用户已是认证卖家");
        user.setRole(Constants.ROLE_SELLER);
        userMapper.updateById(user);
        log.info("[升级] 用户 {} 升级为认证卖家", user.getUsername());
    }

    @Override
    public List<User> listAll() {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime));
    }

    @Override
    public List<Map<String, Object>> countByRole() {
        return userMapper.countByRole();
    }
}