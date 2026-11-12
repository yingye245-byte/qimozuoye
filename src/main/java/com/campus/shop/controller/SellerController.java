package com.campus.shop.controller;

import com.campus.shop.config.LoginUser;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.User;
import com.campus.shop.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 卖家中心：经营看板 / 我的商品 / 留言回复 / 申请认证
 */
@Slf4j
@Controller
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ItemService itemService;
    private final OrderService orderService;
    private final ExtraService extraService;
    private final UserService userService;
    private final MailService mailService;

    /** 卖家中心首页（含 ECharts 图表数据） */
    @GetMapping({"", "/", "/index"})
    public String index(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Map<String, Object> stat = orderService.sellerStat(loginUser.getId());
        model.addAttribute("stat", stat);
        model.addAttribute("myItems", itemService.listMyItems(loginUser.getId(), null));
        return "seller/index";
    }

    /** 我的商品 */
    @GetMapping("/items")
    public String items(@RequestParam(required = false) Integer auditStatus,
                        @AuthenticationPrincipal LoginUser loginUser,
                        Model model) {
        model.addAttribute("items", itemService.listMyItems(loginUser.getId(), auditStatus));
        model.addAttribute("auditStatus", auditStatus);
        return "seller/items";
    }

    /** 留言管理 */
    @GetMapping("/comments")
    public String comments(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("comments", extraService.listSellerComments(loginUser.getId()));
        return "seller/comments";
    }

    /** 回复留言 */
    @PostMapping("/comments/{id}/reply")
    public String reply(@PathVariable Long id,
                        @RequestParam String reply,
                        @AuthenticationPrincipal LoginUser loginUser,
                        RedirectAttributes ra) {
        try {
            extraService.replyComment(id, loginUser.getId(), reply);
            ra.addFlashAttribute("success", "回复成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller/comments";
    }

    /**
     * 申请成为认证卖家：直接把角色升到 SELLER，并异步发邮件通知。
     * 真实系统会走人工审核，这里为演示做了简化，但保留完整的异步通知链路。
     */
    @PostMapping("/apply")
    public String apply(@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes ra) {
        try {
            userService.promoteToSeller(loginUser.getId());
            User me = userService.getById(loginUser.getId());
            mailService.send(me.getEmail(), "【校园二手】卖家认证已通过",
                    "你好 " + me.getNickname() + "，你的卖家认证已通过，现在可以开始发布闲置物品了。");
            ra.addFlashAttribute("success", "卖家认证已开通，请重新登录后生效");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller";
    }

    @ResponseBody
    @GetMapping("/stat")
    public Map<String, Object> stat(@AuthenticationPrincipal LoginUser loginUser) {
        return orderService.sellerStat(loginUser.getId());
    }
}