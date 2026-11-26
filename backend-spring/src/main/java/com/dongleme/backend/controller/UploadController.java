package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * 聊天图片上传：只接受常见图片格式，文件名由服务端生成，避免路径穿越。
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final Path uploadDir;

    public UploadController(@Value("${app.upload.dir:uploads}") String dir) {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    @PostMapping("/image")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择图片");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw ApiException.badRequest("仅支持 JPG、PNG、WebP、GIF 图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw ApiException.badRequest("图片不能超过 5MB");
        }

        try {
            byte[] bytes = file.getBytes();
            // 内容寻址：同一张图片只存一份，文件名即内容哈希，可长期缓存
            String name = sha256(bytes) + extension;
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(name);
            if (!Files.exists(target)) {
                Files.write(target, bytes);
            }
            return Map.of("url", "/uploads/" + name);
        } catch (IOException error) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception error) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "图片处理失败");
        }
    }
}
