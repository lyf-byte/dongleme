package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.MarkReadRequest;
import com.dongleme.backend.dto.NotificationView;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationView> list(@AuthenticationPrincipal AuthUser principal) {
        return notificationService.list(requireId(principal));
    }

    @PutMapping("/read")
    public Map<String, Object> read(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) MarkReadRequest request) {
        notificationService.markRead(requireId(principal), request == null ? null : request.id());
        return Map.of("ok", true);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
