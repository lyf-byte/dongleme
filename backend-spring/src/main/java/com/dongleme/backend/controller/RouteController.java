package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.RouteCreateRequest;
import com.dongleme.backend.dto.RouteDetailView;
import com.dongleme.backend.dto.RouteSaveResult;
import com.dongleme.backend.dto.RouteSummaryView;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.RouteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public RouteSaveResult create(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody RouteCreateRequest request) {
        return routeService.create(requireId(principal), request);
    }

    @GetMapping
    public Map<String, Object> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(defaultValue = "20") int limit) {
        List<RouteSummaryView> routes = routeService.list(requireId(principal), limit);
        return Map.of("routes", routes);
    }

    @GetMapping("/{id}")
    public RouteDetailView detail(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable long id) {
        return routeService.detail(requireId(principal), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable long id) {
        return routeService.delete(requireId(principal), id);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
