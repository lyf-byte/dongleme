package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.SummaryRequest;
import com.dongleme.backend.dto.SummaryView;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.SummaryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping
    public SummaryView post(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) SummaryRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return summaryService.generate(
                request == null ? new SummaryRequest(null, null) : request,
                principal.id());
    }
}
