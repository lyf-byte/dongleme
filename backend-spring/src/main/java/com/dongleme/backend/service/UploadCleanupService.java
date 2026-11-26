package com.dongleme.backend.service;

import com.dongleme.backend.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 清理没有被任何消息引用的临时图片（上传后未发送、撤销发送等）。
 * 被消息引用的图片会一直保留，避免旧聊天图片失效。
 */
@Service
public class UploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupService.class);
    private static final long ORPHAN_AGE_MS = 24L * 60 * 60 * 1000;

    private final Path uploadDir;
    private final MessageMapper messageMapper;

    public UploadCleanupService(
            @Value("${app.upload.dir:uploads}") String dir,
            MessageMapper messageMapper) {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        this.messageMapper = messageMapper;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void cleanupOrphans() {
        if (!Files.isDirectory(uploadDir)) {
            return;
        }
        long deadline = System.currentTimeMillis() - ORPHAN_AGE_MS;
        try (Stream<Path> files = Files.list(uploadDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toMillis() > deadline) {
                        return;
                    }
                    String url = "/uploads/" + path.getFileName();
                    if (messageMapper.countByMediaUrl(url) == 0) {
                        Files.deleteIfExists(path);
                        log.info("已清理未引用的聊天图片: {}", url);
                    }
                } catch (IOException error) {
                    log.warn("清理图片失败: {}", path.getFileName(), error);
                }
            });
        } catch (IOException error) {
            log.warn("扫描图片目录失败", error);
        }
    }
}
