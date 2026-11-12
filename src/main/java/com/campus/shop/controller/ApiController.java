package com.campus.shop.controller;

import com.campus.shop.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 通用 AJAX 接口：图片上传
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    /** 上传目录：D:/projects/uploads（按日期分子目录） */
    @Value("${campus.upload.path:D:/projects/uploads}")
    private String uploadPath;

    private static final Set<String> ALLOW = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择文件");
        }
        String original = Objects.requireNonNull(file.getOriginalFilename());
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOW.contains(ext)) {
            return Result.fail("只允许上传 jpg/png/gif/webp 图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.fail("图片不能超过 5MB");
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File dir = new File(uploadPath, datePath);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.fail("创建上传目录失败：" + dir.getAbsolutePath());
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dest = new File(dir, name);
        try {
            file.transferTo(dest.getAbsoluteFile());
        } catch (IOException e) {
            log.error("[上传] 保存失败", e);
            return Result.fail("上传失败：" + e.getMessage());
        }
        Map<String, String> data = new HashMap<>();
        data.put("url", "/uploads/" + datePath + "/" + name);
        data.put("name", name);
        return Result.success(data);
    }
}