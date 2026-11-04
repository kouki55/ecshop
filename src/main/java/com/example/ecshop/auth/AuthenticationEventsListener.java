package com.example.ecshop.auth;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Security が発行するログインの成功・失敗イベントを受け取り、LoginAttemptService に伝える。
 * 失敗のうち数えるのは「パスワード違い」だけ。仮登録（DisabledException）や
 * ロック中（LockedException）はパスワードを照合する前に拒否されるので、別のイベントになり数えない。
 */
@Component
public class AuthenticationEventsListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventsListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        // getName() はフォームに入力されたメールアドレス
        loginAttemptService.recordFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AppUserDetails user) {
            loginAttemptService.recordSuccess(user.getUserId());
        }
    }
}
