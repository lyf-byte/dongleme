package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.AppVersionView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Android 版本更新：客户端启动时拉取最新版本号与 APK 地址。
 * 接口公开，不要求登录，方便登录页也能检查更新。
 */
@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    private final int versionCode;
    private final String versionName;
    private final String apkUrl;
    private final String apkPath;
    private final String notes;
    private final boolean force;

    public AppVersionController(
            @Value("${app.android.version-code:2}") int versionCode,
            @Value("${app.android.version-name:1.1}") String versionName,
            @Value("${app.android.apk-url:}") String apkUrl,
            @Value("${app.android.apk-path:}") String apkPath,
            @Value("${app.android.notes:}") String notes,
            @Value("${app.android.force:false}") boolean force) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.apkPath = apkPath;
        this.notes = notes;
        this.force = force;
    }

    @GetMapping("/version")
    public AppVersionView version() {
        String url = apkUrl;
        if (url == null || url.isBlank()) {
            url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/app/download")
                    .toUriString();
        }
        return new AppVersionView(versionCode, versionName, url, notes, force);
    }

    /** 从服务器本地目录直接下发 APK；部署时把文件放到 app.android.apk-path 指定的位置。 */
    @GetMapping("/download")
    public ResponseEntity<Resource> download() throws IOException {
        if (apkPath == null || apkPath.isBlank()) {
            throw ApiException.notFound("安装包暂未上传");
        }
        Path path = Paths.get(apkPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw ApiException.notFound("安装包暂未上传");
        }
        String fileName = "dongleme-" + versionName + ".apk";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }
}
