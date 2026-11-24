package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.AuthRequest;
import com.dongleme.backend.dto.AuthResponse;
import com.dongleme.backend.dto.AuthUpdateRequest;
import com.dongleme.backend.dto.UserView;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.JwtService;
import com.dongleme.backend.security.LoginThrottle;
import com.dongleme.backend.security.UserLookupCache;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PHONE_RE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_RE = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserLookupCache userLookupCache;
    private final LoginThrottle loginThrottle;

    /**
     * 账号不存在时也要跑一次 bcrypt，否则「查无此人」会比「密码错误」快一个数量级，
     * 攻击者可以用响应时间枚举出哪些手机号/邮箱注册过。
     */
    private final String decoyHash;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserLookupCache userLookupCache,
            LoginThrottle loginThrottle) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userLookupCache = userLookupCache;
        this.loginThrottle = loginThrottle;
        this.decoyHash = passwordEncoder.encode("dongleme-timing-decoy");
    }

    public AuthResponse handle(AuthRequest request, String clientIp) {
        String action = request.action() == null ? "" : request.action();
        return switch (action) {
            case "register" -> register(request);
            case "login" -> login(request, clientIp);
            default -> throw ApiException.badRequest("未知的认证操作");
        };
    }

    public User requireUser(String id) {
        User user = userLookupCache.get(id);
        if (user == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return applyDueGoal(user);
    }

    /**
     * 「设为下周目标」存的是待生效值，到生效周自动切换为当前周目标。
     * 只在到期后的第一次读取时写一次库，之后 pending 字段已清空。
     */
    private User applyDueGoal(User user) {
        Integer nextGoal = user.getNextWeeklyGoal();
        LocalDate dueWeek = user.getNextGoalWeek();
        if (nextGoal == null
                || dueWeek == null
                || dueWeek.isAfter(ShanghaiTime.weekStart(ShanghaiTime.today()))) {
            return user;
        }

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getWeeklyGoal, nextGoal)
                .set(User::getNextWeeklyGoal, null)
                .set(User::getNextGoalWeek, null));
        userLookupCache.invalidate(user.getId());

        User refreshed = userLookupCache.get(user.getId());
        return refreshed == null ? user : refreshed;
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
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("密码至少 " + MIN_PASSWORD_LENGTH + " 位");
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

    private AuthResponse login(AuthRequest request, String clientIp) {
        String account = normalize(request.account());
        loginThrottle.checkAllowed(clientIp, account);

        AccountLookup lookup = findUser(account);
        String password = request.password() == null ? "" : request.password();

        if (lookup.user() == null) {
            // 账号不存在时消耗等量时间，避免通过响应延迟枚举账号
            passwordEncoder.matches(password, decoyHash);
            loginThrottle.recordFailure(clientIp, account);
            // 产品明确要求区分提示：账号不存在时提示「用户不存在」
            throw ApiException.unauthorized("用户不存在");
        }

        if (!passwordEncoder.matches(password, lookup.user().getPasswordHash())) {
            loginThrottle.recordFailure(clientIp, account);
            throw ApiException.unauthorized("账号或密码错误");
        }

        loginThrottle.recordSuccess(account);
        return new AuthResponse(sign(lookup.user()), UserView.of(lookup.user()));
    }

    private Map<String, Object> setOnboarded(Boolean onboarded, String userId) {
        User current = requireUser(userId);
        User patch = new User();
        patch.setId(current.getId());
        patch.setOnboarded(Boolean.TRUE.equals(onboarded));
        userMapper.updateById(patch);
        userLookupCache.invalidate(userId);

        User saved = requireUser(userId);
        return Map.of("user", UserView.of(saved));
    }

    private Map<String, Object> changePassword(AuthUpdateRequest request, String userId) {
        String newPassword = request.newPassword() == null ? "" : request.newPassword();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("新密码至少 " + MIN_PASSWORD_LENGTH + " 位");
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
