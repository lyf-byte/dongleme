package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.ChatHistoryView;
import com.dongleme.backend.dto.ConversationView;
import com.dongleme.backend.dto.MarkReadRequest;
import com.dongleme.backend.dto.MessageView;
import com.dongleme.backend.dto.SendMessageRequest;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversations")
    public List<ConversationView> conversations(@AuthenticationPrincipal AuthUser principal) {
        return messageService.conversations(requireId(principal));
    }

    @GetMapping("/unread")
    public Map<String, Object> unread(@AuthenticationPrincipal AuthUser principal) {
        return messageService.unread(requireId(principal));
    }

    @GetMapping
    public ChatHistoryView history(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam("userId") String userId,
            @RequestParam(name = "beforeId", required = false) Long beforeId) {
        return messageService.history(requireId(principal), userId, beforeId);
    }

    @PostMapping
    public MessageView send(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody SendMessageRequest request) {
        return messageService.send(requireId(principal), request);
    }

    @PostMapping("/{id}/recall")
    public Map<String, Object> recall(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable("id") long id) {
        return messageService.recall(requireId(principal), id);
    }

    @PutMapping("/read")
    public Map<String, Object> read(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody MarkReadRequest request) {
        return messageService.markRead(requireId(principal), request == null ? null : request.userId());
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
