package com.campus.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.shop.config.LoginUser;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.Order;
import com.campus.shop.entity.User;
import com.campus.shop.service.*;
import com.campus.shop.task.ScheduledTasks;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 后台管理（仅 ROLE_ADMIN）
 * <p>类级别用 @PreAuthorize 二次加固，SecurityConfig 里已配 /admin/** 需要 ADMIN。</p>
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ItemService itemService;
    private final UserService userService;
    private final OrderService orderService;
    private final ExtraService extraService;
    private final ScheduledTasks scheduledTasks;

    /** 数据看板（ECharts） */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("stat", extraService.adminDashboard());
        return "admin/dashboard";
    }

    /** 商品审核列表 */
    @GetMapping("/audit")
    public String auditList(Model model) {
        List<Item> pending = itemService.listPendingAudit();
        model.addAttribute("pending", pending);
        model.addAttribute("count", pending.size());
        return "admin/audit";
    }

    /** 审核通过 / 驳回 */
    @PostMapping("/audit/{id}")
    public String audit(@PathVariable Long id,
                        @RequestParam boolean pass,
                        RedirectAttributes ra) {
        try {
            itemService.audit(id, pass);
            ra.addFlashAttribute("success", pass ? "已通过审核" : "已驳回");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/audit";
    }

    /** 用户管理 */
    @GetMapping("/users")
    public String users(Model model) {
        List<User> users = userService.listAll();
        users.forEach(u -> u.setPassword(null));
        model.addAttribute("users", users);
        return "admin/users";
    }

    /** 启用 / 禁用用户 */
    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id,
                             @AuthenticationPrincipal LoginUser loginUser,
                             RedirectAttributes ra) {
        if (id.equals(loginUser.getId())) {
            ra.addFlashAttribute("error", "不能禁用自己");
            return "redirect:/admin/users";
        }
        try {
            userService.toggleStatus(id);
            ra.addFlashAttribute("success", "操作成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** 把用户升级为认证卖家 */
    @PostMapping("/users/{id}/promote")
    public String promote(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userService.promoteToSeller(id);
            ra.addFlashAttribute("success", "已升级为认证卖家");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** 全部订单 */
    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) Integer status,
                         @RequestParam(defaultValue = "1") Integer page,
                         Model model) {
        IPage<Order> p = orderService.pageAdmin(status, page, 15);
        model.addAttribute("page", p);
        model.addAttribute("status", status);
        return "admin/orders";
    }

    /** 管理员强制取消订单（违规处理，同时释放商品库存） */
    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        try {
            orderService.adminCancel(id, reason);
            ra.addFlashAttribute("success", "订单已取消并释放商品");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders";
    }

    /* ---------- 图表数据接口（ECharts 异步加载） ---------- */

    @ResponseBody
    @GetMapping(value = "/chart/category", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> chartCategory() {
        return extraService.categoryDistribution();
    }

    @ResponseBody
    @GetMapping(value = "/chart/deal", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> chartDeal(@RequestParam(defaultValue = "7") int days) {
        return extraService.dealTrend(days);
    }

    @ResponseBody
    @GetMapping(value = "/chart/status", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> chartStatus() {
        return extraService.orderStatusDistribution();
    }

    @ResponseBody
    @GetMapping(value = "/chart/seller", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> chartSeller() {
        return extraService.sellerRank(5);
    }

    @ResponseBody
    @GetMapping(value = "/chart/role", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> chartRole() {
        return userService.countByRole();
    }

    /**
     * 手动触发超时订单扫描（演示用：答辩时不用等到 5 分钟的 cron 触发）。
     */
    @PostMapping("/task/scan-timeout")
    public String triggerScanTimeout(RedirectAttributes ra) {
        try {
            int n = scheduledTasks.doScanTimeoutOrders();
            ra.addFlashAttribute("success", "超时订单扫描完成，本次取消 " + n + " 笔");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "扫描失败：" + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * 手动触发成交统计归档（演示用：把昨天之前的成交数据写入 transaction_stat）。
     */
    @PostMapping("/task/stat")
    public String triggerStat(RedirectAttributes ra) {
        try {
            int n = scheduledTasks.statYesterday();
            ra.addFlashAttribute("success", "成交统计归档完成，共写入 " + n + " 天数据");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "统计失败：" + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * 手动清空看板缓存（演示 Redis 缓存失效）。
     */
    @PostMapping("/task/clear-cache")
    public String triggerClearCache(RedirectAttributes ra) {
        try {
            scheduledTasks.clearDashboardCache();
            ra.addFlashAttribute("success", "看板缓存已清空，下次访问将重新查库");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "清空失败：" + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }
}