package com.campus.shop.controller;

import com.campus.shop.common.Result;
import com.campus.shop.config.LoginUser;
import com.campus.shop.dto.RegisterDTO;
import com.campus.shop.entity.User;
import com.campus.shop.service.ExtraService;
import com.campus.shop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 用户：注册 / 个人中心 / 修改资料 / 修改密码 / 我的想要清单 / 浏览记录
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ExtraService extraService;

    /** 注册 */
    @PostMapping("/register")
    public String register(@Valid RegisterDTO dto,
                           @RequestParam(required = false) String confirmPassword,
                           Model model,
                           RedirectAttributes ra,
                           jakarta.servlet.http.HttpServletRequest request) {
        if (confirmPassword != null && !confirmPassword.equals(dto.getPassword())) {
            model.addAttribute("error", "两次输入的密码不一致");
            model.addAttribute("dto", dto);
            return "register";
        }
        try {
            userService.register(dto);
            try {
                // 注册后自动登录：用户注册完直接进入"发布闲置"，体验更顺
                request.login(dto.getUsername(), dto.getPassword());
                ra.addFlashAttribute("success", "注册成功，欢迎加入校园二手！");
                return "redirect:/item/publish";
            } catch (Exception loginEx) {
                log.warn("[注册] 自动登录失败，跳转登录页：{}", loginEx.getMessage());
                ra.addFlashAttribute("success", "注册成功，请登录");
                return "redirect:/login";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("dto", dto);
            return "register";
        }
    }

    /** 登录成功提示统一由 GlobalModelAdvice.pendingMsg() 提供，这里不再重复声明。 */

    /** 个人中心 */
    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        User me = userService.getById(loginUser.getId());
        model.addAttribute("me", me);
        model.addAttribute("browseList", extraService.listRecentBrowse(loginUser.getId(), 10));
        return "user/profile";
    }

    /** 修改资料 */
    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                @RequestParam String nickname,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String avatar,
                                RedirectAttributes ra) {
        try {
            userService.updateProfile(loginUser.getId(), nickname, email, avatar);
            ra.addFlashAttribute("success", "资料已更新");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    /** 修改密码 */
    @PostMapping("/profile/password")
    public String changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/profile";
        }
        try {
            userService.changePassword(loginUser.getId(), oldPassword, newPassword);
            ra.addFlashAttribute("success", "密码修改成功，请重新登录");
            return "redirect:/logout";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        }
    }

    /** 我的想要清单 */
    @GetMapping("/profile/cart")
    public String cart(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("carts", extraService.listCart(loginUser.getId()));
        return "user/cart";
    }

    /** 移出想要清单 */
    @PostMapping("/profile/cart/{id}/remove")
    public String removeCart(@PathVariable Long id,
                             @AuthenticationPrincipal LoginUser loginUser,
                             RedirectAttributes ra) {
        extraService.removeCart(loginUser.getId(), id);
        ra.addFlashAttribute("success", "已移出清单");
        return "redirect:/profile/cart";
    }

    /* ==================== 兼容旧示例接口 ==================== */

    @ResponseBody
    @GetMapping("/api/user/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User u = userService.getById(id);
        if (u != null) u.setPassword(null);
        return Result.success(u);
    }
}