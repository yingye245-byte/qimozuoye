package com.campus.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.shop.config.LoginUser;
import com.campus.shop.entity.Order;
import com.campus.shop.service.ExtraService;
import com.campus.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 订单：下单 / 支付 / 确认 / 收货 / 取消 / 列表
 */
@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ExtraService extraService;

    /** 下单（从商品详情页发起） */
    @PostMapping("/create")
    public String create(@RequestParam Long itemId,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        try {
            Order order = orderService.create(itemId, loginUser.getId());
            ra.addFlashAttribute("success", "下单成功，请在 30 分钟内完成支付");
            return "redirect:/order/detail/" + order.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/item/detail/" + itemId;
        }
    }

    /** 我买到的 */
    @GetMapping("/buyer")
    public String buyer(@RequestParam(required = false) Integer status,
                        @RequestParam(defaultValue = "1") Integer page,
                        @AuthenticationPrincipal LoginUser loginUser,
                        Model model) {
        IPage<Order> p = orderService.pageBuyer(loginUser.getId(), status, page, 10);
        model.addAttribute("page", p);
        model.addAttribute("status", status);
        model.addAttribute("tab", "buyer");
        return "order/list";
    }

    /** 我卖出的 */
    @GetMapping("/seller")
    public String seller(@RequestParam(required = false) Integer status,
                         @RequestParam(defaultValue = "1") Integer page,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model) {
        IPage<Order> p = orderService.pageSeller(loginUser.getId(), status, page, 10);
        model.addAttribute("page", p);
        model.addAttribute("status", status);
        model.addAttribute("tab", "seller");
        return "order/list";
    }

    /** 订单详情 */
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model,
                         RedirectAttributes ra) {
        boolean isAdmin = loginUser.getRole() != null && loginUser.getRole() == 3;
        Order order = orderService.getDetail(id, loginUser.getId(), isAdmin);
        if (order == null) {
            ra.addFlashAttribute("error", "订单不存在或无权查看");
            return "redirect:/order/buyer";
        }
        model.addAttribute("order", order);
        model.addAttribute("remainSeconds", orderService.remainingSeconds(order));
        return "order/detail";
    }

    /** 支付 */
    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id,
                      @AuthenticationPrincipal LoginUser loginUser,
                      RedirectAttributes ra) {
        return doAction(ra, () -> orderService.pay(id, loginUser.getId()), "支付成功", "/order/detail/" + id);
    }

    /** 卖家确认 */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @AuthenticationPrincipal LoginUser loginUser,
                          RedirectAttributes ra) {
        return doAction(ra, () -> orderService.confirm(id, loginUser.getId()), "已确认，等待买家收货", "/order/detail/" + id);
    }

    /** 买家确认收货 */
    @PostMapping("/{id}/finish")
    public String finish(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        return doAction(ra, () -> orderService.finish(id, loginUser.getId()), "交易完成", "/order/detail/" + id);
    }

    /** 取消 */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        String error = doCancel(id, loginUser.getId());
        if (error == null) {
            ra.addFlashAttribute("success", "已取消订单，商品已恢复在售");
        } else {
            ra.addFlashAttribute("error", error);
        }
        return "redirect:/order/detail/" + id;
    }

    /** 取消订单：成功返回 null，失败返回失败原因 */
    private String doCancel(Long id, Long userId) {
        try {
            orderService.cancel(id, userId);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @FunctionalInterface
    interface Action {
        void run();
    }

    private String doAction(RedirectAttributes ra, Action action, String ok, String redirect) {
        try {
            action.run();
            ra.addFlashAttribute("success", ok);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirect;
    }
}