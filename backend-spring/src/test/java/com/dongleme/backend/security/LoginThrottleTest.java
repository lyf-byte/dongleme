package com.dongleme.backend.security;

import com.dongleme.backend.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleTest {

    private static final String IP = "1.2.3.4";
    private static final String ACCOUNT = "13800000000";

    private static LoginThrottle throttle(int accountMax, int ipMax, long windowSeconds) {
        return new LoginThrottle(accountMax, ipMax, windowSeconds);
    }

    private static void failTimes(LoginThrottle throttle, String ip, String account, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(ip, account);
        }
    }

    @Test
    @DisplayName("同一账号连续失败 5 次后，第 6 次直接被拒绝")
    void blocksAfterAccountLimit() {
        LoginThrottle throttle = throttle(5, 30, 900);
        failTimes(throttle, IP, ACCOUNT, 5);

        assertThatThrownBy(() -> throttle.checkAllowed(IP, ACCOUNT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("未达阈值时放行")
    void allowsBelowLimit() {
        LoginThrottle throttle = throttle(5, 30, 900);
        failTimes(throttle, IP, ACCOUNT, 4);

        assertThatCode(() -> throttle.checkAllowed(IP, ACCOUNT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("账号维度是全局的：换 IP 也拦得住同一个账号")
    void accountLimitIsGlobalAcrossIps() {
        LoginThrottle throttle = throttle(5, 30, 900);
        failTimes(throttle, "1.1.1.1", ACCOUNT, 3);
        failTimes(throttle, "2.2.2.2", ACCOUNT, 2);

        assertThatThrownBy(() -> throttle.checkAllowed("3.3.3.3", ACCOUNT))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("不同账号互不影响")
    void otherAccountUnaffected() {
        LoginThrottle throttle = throttle(5, 30, 900);
        failTimes(throttle, IP, ACCOUNT, 5);

        assertThatCode(() -> throttle.checkAllowed(IP, "13900000000"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("同一 IP 撞很多账号时，由 IP 维度兜住")
    void ipLimitStopsSpraying() {
        LoginThrottle throttle = throttle(1000, 3, 900);
        throttle.recordFailure(IP, "a");
        throttle.recordFailure(IP, "b");
        throttle.recordFailure(IP, "c");

        assertThatThrownBy(() -> throttle.checkAllowed(IP, "d"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("登录成功后清空该账号的失败计数")
    void successClearsAccountCounter() {
        LoginThrottle throttle = throttle(5, 30, 900);
        failTimes(throttle, IP, ACCOUNT, 4);
        throttle.recordSuccess(ACCOUNT);
        failTimes(throttle, IP, ACCOUNT, 4);

        assertThatCode(() -> throttle.checkAllowed(IP, ACCOUNT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("窗口过期后计数清零，重新放行")
    void windowExpiryResetsCounter() {
        LoginThrottle throttle = throttle(5, 30, 0);
        failTimes(throttle, IP, ACCOUNT, 10);

        assertThatCode(() -> throttle.checkAllowed(IP, ACCOUNT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("账号大小写与首尾空格归一化后视为同一账号")
    void accountKeyIsNormalized() {
        LoginThrottle throttle = throttle(3, 30, 900);
        throttle.recordFailure(IP, "Test@Example.com");
        throttle.recordFailure(IP, "test@example.com");
        throttle.recordFailure(IP, "  TEST@EXAMPLE.COM  ");

        assertThatThrownBy(() -> throttle.checkAllowed(IP, "test@example.com"))
                .isInstanceOf(ApiException.class);
    }
}
