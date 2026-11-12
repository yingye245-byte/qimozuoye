package com.campus.shop.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 注册表单
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在 3-20 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称最多 20 个字")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空（订单通知会发送到该邮箱）")
    private String email;

    /** 1 普通用户，2 认证卖家 */
    private Integer role = 1;
}