package com.campus.shop.controller;

import com.campus.shop.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传：保存到 campus.upload.path（默认 D:/projects/uploads/yyyy/MM/），
 * 返回值是可直接被 &lt;img&gt; 使用的相对 URL。
 */
@Slf4j
@Controller
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    @Value("${campus.upload.path:D:/projects/uploads}")
    private String uploadPath;

    @PostMapping("/image")
    @ResponseBody
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail("图片不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            return Result.fail("只支持 jpg / jpeg / png / gif / webp 格式");
        }

        String sub = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        File dir = new File(uploadPath, sub);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("[上传] 目录创建失败 {}", dir.getAbsolutePath());
            return Result.fail("服务器存储目录不可用");
        }

        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dest = new File(dir, name);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("[上传] 保存失败", e);
            return Result.fail("图片保存失败");
        }
        String url = "/uploads/" + sub + "/" + name;
        log.info("[上传] {} -> {}", original, dest.getAbsolutePath());
        return Result.success(url);
    }
}