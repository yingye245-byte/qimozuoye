package com.campus.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.shop.dto.ItemDTO;
import com.campus.shop.dto.ItemQuery;
import com.campus.shop.entity.Category;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.ItemImage;

import java.util.List;

/**
 * 商品服务
 */
public interface ItemService {

    /** 前台商品分页（带多条件筛选） */
    IPage<Item> pageForFront(ItemQuery query);

    /** 商品详情（带缓存防穿透） */
    Item getDetail(Long id);

    /** 商品图片列表 */
    List<ItemImage> listImages(Long itemId);

    /** 发布商品 */
    Long publish(ItemDTO dto, Long userId);

    /** 编辑商品 */
    void updateItem(ItemDTO dto, Long userId);

    /** 卖家上下架 */
    void toggleSale(Long itemId, Long userId);

    /** 卖家删除 */
    void deleteItem(Long itemId, Long userId);

    /** 我发布的 */
    List<Item> listMyItems(Long userId, Integer auditStatus);

    /** 管理员审核 */
    void audit(Long itemId, boolean pass);

    /** 待审核列表 */
    List<Item> listPendingAudit();

    /** 相关推荐 */
    List<Item> listRelated(Long categoryId, Long excludeId, int limit);

    /** 分类树 */
    List<Category> listTopCategories();

    List<Category> listChildCategories(Long parentId);

    List<Category> listAllCategories();

    /** 分类树（顶级含 children，用于发布页分组选择） */
    List<Category> listCategoryTree();

    /** 缓存失效工具：商品变更后清相关缓存 */
    void evictItemCache(Long itemId);

    /** 首页热门：成交量高的已上架商品 */
    List<Item> listHot(int limit);

    /** 首页最新：最新审核通过的已上架商品 */
    List<Item> listNewest(int limit);

    /** 搜索建议 */
    List<String> listRecentTitles(int limit);
}