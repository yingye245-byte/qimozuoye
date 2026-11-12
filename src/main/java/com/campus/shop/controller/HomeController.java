package com.campus.shop.controller;

import com.campus.shop.config.LoginUser;
import com.campus.shop.entity.*;
import com.campus.shop.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 首页 / 登录 / 注册 / 商品发布
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ItemService itemService;

    @GetMapping({"/", "/index"})
    public String index(Model model) {
        model.addAttribute("hotItems", itemService.listHot(8));
        model.addAttribute("newItems", itemService.listNewest(8));
        model.addAttribute("categories", itemService.listAllCategories());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/403")
    public String forbidden() {
        return "error/403";
    }

    /**
     * 发布商品页面（需要登录）
     */
    @GetMapping("/item/publish")
    public String publishPage(Model model, @AuthenticationPrincipal LoginUser loginUser) {
        model.addAttribute("categories", itemService.listAllCategories());
        model.addAttribute("categoryTree", itemService.listCategoryTree());
        return "item/publish";
    }
}