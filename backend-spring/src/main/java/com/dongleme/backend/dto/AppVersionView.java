package com.dongleme.backend.dto;

/**
 * Android 客户端版本信息。字段名与 App 端 Gson 模型保持一致。
 */
public record AppVersionView(
        int versionCode,
        String versionName,
        String url,
        String notes,
        boolean force
) {
}
