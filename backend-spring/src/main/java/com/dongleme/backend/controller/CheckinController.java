package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.CheckinRequest;
import com.dongleme.backend.dto.CheckinResult;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.CheckinService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping
    public CheckinResult post(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody CheckinRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return checkinService.checkin(principal.id(), request);
    }
}
