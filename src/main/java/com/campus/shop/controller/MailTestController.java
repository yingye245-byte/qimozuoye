package com.campus.shop.controller;

import com.campus.shop.common.Result;
import com.campus.shop.config.LoginUser;
import com.campus.shop.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 测试用接口：给当前登录用户自己的邮箱发一封测试邮件，验证 SMTP 配置是否可用。
 * <p>答辩或部署自检时可以打开 /api/mail/test 看结果。</p>
 */
@RestController
@RequiredArgsConstructor
public class MailTestController {

    private final MailService mailService;

    @GetMapping("/api/mail/test")
    public Result test(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return Result.error(401, "请先登录");
        }
        String to = loginUser.getUser() == null ? null : loginUser.getUser().getEmail();
        if (to == null || to.isBlank()) {
            return Result.error(400, "当前账号没有绑定邮箱，请先在个人中心填写邮箱");
        }
        if (!mailService.isConfigured()) {
            return Result.error(500, "邮箱未配置完整：请检查 application-dev.yml 的 spring.mail.username 与 password（QQ 邮箱授权码）");
        }
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        boolean ok = mailService.send(to, "【校园二手】邮件服务测试",
                "这是一封测试邮件，发送时间：" + time + "\n如果收到说明 JavaMailSender + QQ SMTP 配置成功。");
        return ok ? Result.success("测试邮件已发送至 " + to + "，请查收（可能在垃圾箱）")
                : Result.error(500, "发送失败，请查看后台日志中的 [邮件] 记录");
    }
}