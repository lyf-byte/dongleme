package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.AuthRequest;
import com.dongleme.backend.dto.AuthResponse;
import com.dongleme.backend.dto.AuthUpdateRequest;
import com.dongleme.backend.dto.UserView;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.JwtService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PHONE_RE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_RE = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse handle(AuthRequest request) {
        String action = request.action() == null ? "" : request.action();
        return switch (action) {
            case "register" -> register(request);
            case "login" -> login(request);
            default -> throw ApiException.badRequest("未知的认证操作");
        };
    }

    public User requireUser(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return user;
    }

    public Map<String, Object> update(AuthUpdateRequest request, String userId) {
        String action = request.action() == null ? "" : request.action();
        return switch (action) {
            case "onboarded" -> setOnboarded(request.onboarded(), userId);
            case "password" -> changePassword(request, userId);
            default -> throw ApiException.badRequest("未知的认证操作");
        };
    }

    private AuthResponse register(AuthRequest request) {
        String account = normalize(request.account());
        String password = request.password() == null ? "" : request.password();
        AccountLookup lookup = findUser(account);

        if (!"phone".equals(lookup.type()) && !"email".equals(lookup.type())) {
            throw ApiException.badRequest("请输入正确的手机号或邮箱");
        }
        if (password.length() < 6) {
            throw ApiException.badRequest("密码至少 6 位");
        }
        if (lookup.user() != null) {
            throw ApiException.conflict("phone".equals(lookup.type())
                    ? "该手机号已注册，请直接登录"
                    : "该邮箱已注册，请直接登录");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("email".equals(lookup.type()) ? lookup.value() : null);
        user.setPhone("phone".equals(lookup.type()) ? lookup.value() : null);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname("运动新人");
        user.setGender("secret");
        user.setWeight(new BigDecimal("60"));
        user.setWeeklyGoal(150);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("该账号已注册，请直接登录");
        }

        User saved = userMapper.selectById(user.getId());
        return new AuthResponse(sign(saved), UserView.of(saved));
    }

    private AuthResponse login(AuthRequest request) {
        AccountLookup lookup = findUser(normalize(request.account()));
        String password = request.password() == null ? "" : request.password();

        if (lookup.user() == null
                || !passwordEncoder.matches(password, lookup.user().getPasswordHash())) {
            throw ApiException.unauthorized("账号或密码错误");
        }

        return new AuthResponse(sign(lookup.user()), UserView.of(lookup.user()));
    }

    private Map<String, Object> setOnboarded(Boolean onboarded, String userId) {
        User current = requireUser(userId);
        User patch = new User();
        patch.setId(current.getId());
        patch.setOnboarded(Boolean.TRUE.equals(onboarded));
        userMapper.updateById(patch);

        User saved = requireUser(userId);
        return Map.of("user", UserView.of(saved));
    }

    private Map<String, Object> changePassword(AuthUpdateRequest request, String userId) {
        String newPassword = request.newPassword() == null ? "" : request.newPassword();
        if (newPassword.length() < 6) {
            throw ApiException.badRequest("新密码至少 6 位");
        }

        User user = requireUser(userId);
        String oldPassword = request.oldPassword() == null ? "" : request.oldPassword();
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }

        User patch = new User();
        patch.setId(user.getId());
        patch.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(patch);
        return Map.of("ok", true);
    }

    private String sign(User user) {
        return jwtService.sign(user.getId(), Boolean.TRUE.equals(user.getIsAdmin()));
    }

    private AccountLookup findUser(String account) {
        if (PHONE_RE.matcher(account).matches()) {
            return new AccountLookup(selectByPhone(account), "phone", account);
        }
        if (EMAIL_RE.matcher(account).matches()) {
            String email = account.toLowerCase(Locale.ROOT);
            return new AccountLookup(selectByEmail(email), "email", email);
        }
        if (USERNAME_RE.matcher(account).matches()) {
            String username = account.toLowerCase(Locale.ROOT);
            return new AccountLookup(selectByUsername(username), "username", username);
        }
        return new AccountLookup(null, "", account);
    }

    private User selectByPhone(String phone) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, phone).last("limit 1"));
    }

    private User selectByEmail(String email) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getEmail, email).last("limit 1"));
    }

    private User selectByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username).last("limit 1"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record AccountLookup(User user, String type, String value) {
    }
}
