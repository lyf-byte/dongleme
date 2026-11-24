package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.MakeupRequest;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.MakeupService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/makeup")
public class MakeupController {

    private final MakeupService makeupService;

    public MakeupController(MakeupService makeupService) {
        this.makeupService = makeupService;
    }

    @GetMapping
    public Map<String, Object> status(@AuthenticationPrincipal AuthUser principal) {
        return makeupService.status(requireId(principal));
    }

    @PostMapping
    public Map<String, Object> use(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) MakeupRequest request) {
        return makeupService.use(requireId(principal), request);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
