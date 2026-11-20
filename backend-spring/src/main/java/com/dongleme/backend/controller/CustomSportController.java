package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.CustomSportRequest;
import com.dongleme.backend.dto.CustomSportView;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.CustomSportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sports")
public class CustomSportController {

    private final CustomSportService sportService;

    public CustomSportController(CustomSportService sportService) {
        this.sportService = sportService;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser principal) {
        return Map.of("sports", sportService.list(requireId(principal)));
    }

    @PostMapping
    public Map<String, Object> create(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody CustomSportRequest request) {
        CustomSportView sport = sportService.create(requireId(principal), request);
        return Map.of("sport", sport);
    }

    @PutMapping
    public Map<String, Object> update(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody CustomSportRequest request) {
        CustomSportView sport = sportService.update(requireId(principal), request);
        return Map.of("sport", sport);
    }

    @DeleteMapping
    public Map<String, Object> delete(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(name = "id", required = false) Long id) {
        sportService.delete(requireId(principal), id);
        return Map.of("ok", true);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
