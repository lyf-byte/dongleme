package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.AuthRequest;
import com.dongleme.backend.dto.AuthResponse;
import com.dongleme.backend.dto.AuthUpdateRequest;
import com.dongleme.backend.dto.UserEnvelope;
import com.dongleme.backend.dto.UserView;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public AuthResponse post(@RequestBody AuthRequest request) {
        return authService.handle(request);
    }

    @GetMapping
    public UserEnvelope get(@AuthenticationPrincipal AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return new UserEnvelope(UserView.of(authService.requireUser(principal.id())));
    }

    @PutMapping
    public Map<String, Object> put(
            @RequestBody AuthUpdateRequest request,
            @AuthenticationPrincipal AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return authService.update(request, principal.id());
    }
}
