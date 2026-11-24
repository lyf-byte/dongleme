package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.NextGoalRequest;
import com.dongleme.backend.dto.ProfileUpdateRequest;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Object get(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(name = "export", required = false) String export) {
        String userId = requireId(principal);
        if ("1".equals(export)) {
            return profileService.export(userId);
        }
        return profileService.getProfile(userId);
    }

    @PutMapping
    public Map<String, Object> update(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody ProfileUpdateRequest request) {
        return profileService.update(requireId(principal), request);
    }

    @PutMapping("/next-goal")
    public Map<String, Object> nextGoal(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody NextGoalRequest request) {
        return profileService.setNextWeeklyGoal(requireId(principal), request.weeklyGoal());
    }

    @DeleteMapping
    public Map<String, Object> delete(@AuthenticationPrincipal AuthUser principal) {
        return profileService.clear(requireId(principal));
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
