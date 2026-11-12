package com.campus.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.shop.config.LoginUser;
import com.campus.shop.dto.CommentDTO;
import com.campus.shop.dto.ItemDTO;
import com.campus.shop.dto.ItemQuery;
import com.campus.shop.entity.Item;
import com.campus.shop.service.ExtraService;
import com.campus.shop.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 商品：列表 / 详情 / 发布 / 编辑 / 留言 / 收藏
 */
@Controller
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ExtraService extraService;

    /** 商品列表（多条件筛选 + 分页 + 排序） */
    @GetMapping("/list")
    public String list(ItemQuery query, Model model) {
        IPage<Item> page = itemService.pageForFront(query);
        model.addAttribute("page", page);
        model.addAttribute("query", query);
        model.addAttribute("categories", itemService.listAllCategories());
        return "item/list";
    }

    /** 搜索入口（等价于列表 + keyword） */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "1") Integer page,
                         Model model) {
        ItemQuery q = new ItemQuery();
        q.setKeyword(keyword);
        q.setPage(page);
        IPage<Item> p = itemService.pageForFront(q);
        model.addAttribute("page", p);
        model.addAttribute("query", q);
        model.addAttribute("categories", itemService.listAllCategories());
        model.addAttribute("suggestions", itemService.listRecentTitles(10));
        return "item/list";
    }

    /** 商品详情：记录浏览 + 加载留言 + 推荐 */
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model,
                         RedirectAttributes ra) {
        Item item = itemService.getDetail(id);
        if (item == null) {
            ra.addFlashAttribute("error", "商品不存在或已被删除");
            return "redirect:/item/list";
        }
        extraService.recordBrowse(loginUser == null ? null : loginUser.getId(), id);

        model.addAttribute("item", item);
        model.addAttribute("images", itemService.listImages(id));
        model.addAttribute("comments", extraService.listComments(id));
        model.addAttribute("related", itemService.listRelated(item.getCategoryId(), id, 4));
        boolean isOwner = loginUser != null && item.getUserId().equals(loginUser.getId());
        model.addAttribute("isOwner", isOwner);
        return "item/detail";
    }

    /** 发布商品（表单提交，带图片 URL 数组） */
    @PostMapping("/publish")
    public String publish(@Valid ItemDTO dto,
                          @AuthenticationPrincipal LoginUser loginUser,
                          RedirectAttributes ra) {
        try {
            Long id = itemService.publish(dto, loginUser.getId());
            ra.addFlashAttribute("success", "发布成功，等待管理员审核");
            return "redirect:/item/detail/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/item/publish";
        }
    }

    /** 编辑商品 */
    @PostMapping("/update")
    public String update(@Valid ItemDTO dto,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        try {
            itemService.updateItem(dto, loginUser.getId());
            ra.addFlashAttribute("success", "修改成功，需重新审核");
            return "redirect:/item/detail/" + dto.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/seller/items";
        }
    }

    /** 上下架 */
    @PostMapping("/toggle/{id}")
    public String toggle(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        try {
            itemService.toggleSale(id, loginUser.getId());
            ra.addFlashAttribute("success", "操作成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller/items";
    }

    /** 删除 */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes ra) {
        try {
            itemService.deleteItem(id, loginUser.getId());
            ra.addFlashAttribute("success", "已删除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller/items";
    }

    /** 留言（AJAX 或表单都支持，这里用表单重定向） */
    @PostMapping("/{id}/comment")
    public String comment(@PathVariable Long id,
                          @Valid CommentDTO dto,
                          @AuthenticationPrincipal LoginUser loginUser,
                          RedirectAttributes ra) {
        try {
            extraService.addComment(id, loginUser.getId(), dto.getContent());
            ra.addFlashAttribute("success", "留言成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/item/detail/" + id;
    }

    /** 加入想要清单 */
    @PostMapping("/{id}/cart")
    public String addCart(@PathVariable Long id,
                          @AuthenticationPrincipal LoginUser loginUser,
                          RedirectAttributes ra) {
        try {
            extraService.addCart(loginUser.getId(), id);
            ra.addFlashAttribute("success", "已加入想要清单");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/item/detail/" + id;
    }

    /** 只为了给 Thymeleaf 提供状态名（测试用） */
    @ResponseBody
    @GetMapping("/statuses")
    public Map<String, List<String>> statuses() {
        return Map.of(
                "audit", List.of("待审核", "审核通过", "审核驳回"),
                "deal", List.of("未知", "在售", "已成交", "已关闭"));
    }
}