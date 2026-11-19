package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.AdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public Object get(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String normalized = action == null || action.isBlank() ? "overview" : action;
        return switch (normalized) {
            case "overview" -> adminService.overview();
            case "users" -> adminService.users(query, page, pageSize);
            case "records" -> adminService.records(type, from, to, page, pageSize);
            case "rank" -> adminService.rank();
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "未知的后台操作");
        };
    }

    @DeleteMapping
    public Map<String, Object> delete(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "id", required = false) String id) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return adminService.delete(action == null ? "" : action, id, principal.id());
    }
}
