package com.campus.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.shop.common.BizException;
import com.campus.shop.common.CacheKeys;
import com.campus.shop.common.Constants;
import com.campus.shop.dto.ItemDTO;
import com.campus.shop.dto.ItemQuery;
import com.campus.shop.entity.Category;
import com.campus.shop.entity.Item;
import com.campus.shop.entity.ItemImage;
import com.campus.shop.mapper.CategoryMapper;
import com.campus.shop.mapper.ItemImageMapper;
import com.campus.shop.mapper.ItemMapper;
import com.campus.shop.service.CacheService;
import com.campus.shop.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final CategoryMapper categoryMapper;
    private final CacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public IPage<Item> pageForFront(ItemQuery q) {
        Page<Item> page = new Page<>(
                q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage(),
                q.getSize() == null || q.getSize() < 1 ? 12 : q.getSize());
        return itemMapper.selectPageForFront(page,
                q.getCategoryId(), q.getKeyword(),
                q.getMinPrice(), q.getMaxPrice(),
                q.getSort() == null ? "new" : q.getSort());
    }

    /**
     * 商品详情：走「防穿透 + 防击穿」缓存
     * <p>同一条 URL 被大量用户访问（秒杀式抢二手）时，只有第一个线程查库。</p>
     */
    @Override
    public Item getDetail(Long id) {
        if (id == null) return null;
        String key = CacheKeys.ITEM_DETAIL + id;
        Item item = cacheService.getWithProtect(key, Item.class, CacheKeys.ITEM_DETAIL_TTL,
                () -> itemMapper.selectDetailById(id));
        return item;
    }

    @Override
    public List<ItemImage> listImages(Long itemId) {
        return itemImageMapper.selectByItemId(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(ItemDTO dto, Long userId) {
        Item item = new Item();
        item.setUserId(userId);
        item.setCategoryId(dto.getCategoryId());
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription());
        item.setPrice(dto.getPrice());
        item.setCover(dto.getCover() == null || dto.getCover().isBlank()
                ? "/images/item/default.jpg" : dto.getCover());
        // 新发布的商品默认待审核，管理员通过后才在前台展示
        item.setAuditStatus(Constants.AUDIT_PENDING);
        item.setStatus(Constants.ITEM_ON_SALE);
        item.setDealStatus(Constants.DEAL_NONE);
        item.setDeleted(0);
        itemMapper.insert(item);

        saveImages(item.getId(), dto.getImages());
        log.info("[发布商品] id={} title={} userId={}", item.getId(), item.getTitle(), userId);
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(ItemDTO dto, Long userId) {
        if (dto.getId() == null) throw new BizException("缺少商品ID");
        Item db = itemMapper.selectById(dto.getId());
        if (db == null) throw new BizException("商品不存在");
        if (!db.getUserId().equals(userId)) throw new BizException(403, "只能编辑自己发布的商品");
        if (Integer.valueOf(Constants.DEAL_DONE).equals(db.getDealStatus())) {
            throw new BizException("已成交的商品不能编辑");
        }

        db.setCategoryId(dto.getCategoryId());
        db.setTitle(dto.getTitle().trim());
        db.setDescription(dto.getDescription());
        db.setPrice(dto.getPrice());
        if (dto.getCover() != null && !dto.getCover().isBlank()) {
            db.setCover(dto.getCover());
        }
        // 修改后重新进入待审核
        db.setAuditStatus(Constants.AUDIT_PENDING);
        itemMapper.updateById(db);

        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            itemImageMapper.delete(new LambdaQueryWrapper<ItemImage>()
                    .eq(ItemImage::getItemId, db.getId()));
            saveImages(db.getId(), dto.getImages());
        }
        evictItemCache(db.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSale(Long itemId, Long userId) {
        Item db = itemMapper.selectById(itemId);
        if (db == null) throw new BizException("商品不存在");
        if (!db.getUserId().equals(userId)) throw new BizException(403, "只能操作自己发布的商品");
        if (Integer.valueOf(Constants.DEAL_DONE).equals(db.getDealStatus())) {
            throw new BizException("已成交的商品不能下架");
        }
        db.setStatus(Integer.valueOf(Constants.ITEM_ON_SALE).equals(db.getStatus())
                ? Constants.ITEM_OFF_SALE : Constants.ITEM_ON_SALE);
        itemMapper.updateById(db);
        evictItemCache(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long itemId, Long userId) {
        Item db = itemMapper.selectById(itemId);
        if (db == null) throw new BizException("商品不存在");
        if (!db.getUserId().equals(userId)) throw new BizException(403, "只能删除自己发布的商品");
        if (Integer.valueOf(Constants.DEAL_DONE).equals(db.getDealStatus())) {
            throw new BizException("已成交的商品不能删除");
        }
        itemMapper.deleteById(itemId);
        evictItemCache(itemId);
    }

    @Override
    public List<Item> listMyItems(Long userId, Integer auditStatus) {
        return itemMapper.selectByUserId(userId, auditStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long itemId, boolean pass) {
        Item db = itemMapper.selectById(itemId);
        if (db == null) throw new BizException("商品不存在");
        db.setAuditStatus(pass ? Constants.AUDIT_PASS : Constants.AUDIT_REJECT);
        itemMapper.updateById(db);
        evictItemCache(itemId);
        log.info("[审核] itemId={} 结果={}", itemId, pass ? "通过" : "驳回");
    }

    @Override
    public List<Item> listPendingAudit() {
        return itemMapper.selectByAuditStatus(Constants.AUDIT_PENDING);
    }

    @Override
    public List<Item> listRelated(Long categoryId, Long excludeId, int limit) {
        if (categoryId == null) return List.of();
        return itemMapper.selectRelated(categoryId, excludeId == null ? 0L : excludeId, limit);
    }

    @Override
    public List<Category> listTopCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, 0)
                .orderByAsc(Category::getSort));
    }

    @Override
    public List<Category> listChildCategories(Long parentId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .orderByAsc(Category::getSort));
    }

    @Override
    public List<Category> listAllCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getSort));
    }

    /**
     * 分类树：顶级分类挂 children，用于发布页「先选大类、再选细类」的联动下拉
     */
    @Override
    public List<Category> listCategoryTree() {
        List<Category> all = listAllCategories();
        java.util.Map<Long, Category> topMap = new java.util.LinkedHashMap<>();
        for (Category c : all) {
            if (!c.isChild()) topMap.put(c.getId(), c);
        }
        for (Category c : all) {
            if (!c.isChild()) continue;
            Category parent = topMap.get(c.getParentId());
            if (parent == null) continue;
            c.setParentName(parent.getName());
            c.setTopId(parent.getId());
            if (parent.getChildren() == null) parent.setChildren(new java.util.ArrayList<>());
            parent.getChildren().add(c);
        }
        List<Category> tree = new java.util.ArrayList<>(topMap.values());
        // 没有子分类的顶级分类，自己也作为一个可选项
        for (Category c : tree) {
            if (c.getChildren() == null) c.setChildren(new java.util.ArrayList<>());
        }
        return tree;
    }

    /**
     * 商品数据变更后清理缓存
     * <p>商品详情 key 精确删除；列表 key 用通配批量删除（列表缓存 5 分钟，也能接受自然过期）</p>
     */
    @Override
    public void evictItemCache(Long itemId) {
        if (itemId != null) {
            cacheService.delete(CacheKeys.ITEM_DETAIL + itemId);
        }
        var listKeys = redisTemplate.keys(CacheKeys.ITEM_LIST + "*");
        if (listKeys != null && !listKeys.isEmpty()) {
            redisTemplate.delete(listKeys);
        }
        cacheService.delete(CacheKeys.HOT_ITEMS);
    }

    /**
     * 首页热门：走 Redis 缓存（10 分钟），避免首页每次 8 条全表排序
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Item> listHot(int limit) {
        String key = CacheKeys.HOT_ITEMS + limit;
        List<Item> cached = (List<Item>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        List<Item> list = itemMapper.selectHot(limit);
        redisTemplate.opsForValue().set(key, list, java.time.Duration.ofMinutes(10));
        return list;
    }

    @Override
    public List<Item> listNewest(int limit) {
        return itemMapper.selectNewest(limit);
    }

    @Override
    public List<String> listRecentTitles(int limit) {
        return itemMapper.selectRecentTitles(limit);
    }

    private void saveImages(Long itemId, List<String> images) {
        if (images == null || images.isEmpty()) return;
        int sort = 1;
        for (String url : images) {
            if (url == null || url.isBlank()) continue;
            ItemImage img = new ItemImage();
            img.setItemId(itemId);
            img.setUrl(url);
            img.setSort(sort++);
            itemImageMapper.insert(img);
        }
    }
}