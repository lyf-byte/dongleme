package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.BlockRequest;
import com.dongleme.backend.dto.ConversationSettingRequest;
import com.dongleme.backend.dto.MarkRemarkRequest;
import com.dongleme.backend.dto.ReportRequest;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.ContactService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PutMapping("/remark")
    public Map<String, Object> setRemark(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody MarkRemarkRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return contactService.setRemark(principal.id(), request);
    }

    @PutMapping("/block")
    public Map<String, Object> block(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody BlockRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return contactService.setBlocked(principal.id(), request);
    }

    @PostMapping("/report")
    public Map<String, Object> report(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody ReportRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return contactService.report(principal.id(), request);
    }

    @PutMapping("/conversation")
    public Map<String, Object> conversationSetting(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody ConversationSettingRequest request) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return contactService.updateConversationSetting(principal.id(), request);
    }
}
