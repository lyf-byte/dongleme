package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.JwtService;
import com.dongleme.backend.service.SseService;
import io.jsonwebtoken.Claims;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * EventSource 不能自定义 Authorization 头，所以用查询参数携带 JWT。
 * 该地址在 SecurityConfig 中放行，由这里手动校验 token。
 */
@RestController
@RequestMapping("/api/messages")
public class MessageStreamController {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final SseService sseService;

    public MessageStreamController(
            JwtService jwtService,
            UserMapper userMapper,
            SseService sseService) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.sseService = sseService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        try {
            Claims claims = jwtService.parse(token);
            String userId = claims.getSubject();
            if (userId == null || userMapper.selectById(userId) == null) {
                throw ApiException.unauthorized("登录已过期，请重新登录");
            }
            return sseService.subscribe(userId);
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
    }
}
