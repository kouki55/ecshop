package com.example.ecshop.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 会員のログイン中のセッションを、ほかのブラウザの分も含めてすべて失効させる。
 * 失効したセッションで次に操作すると、Spring Security（ConcurrentSessionFilter）がログアウトさせ、
 * ログイン画面（/login?expired）に送る。
 */
@Service
public class UserSessionService {

    private final SessionRegistry sessionRegistry;

    public UserSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** パスワードが変わったら、古いパスワードでログインしていたセッション（乗っ取られていたかもしれない）を切る。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(AccountEvent.PasswordChanged event) {
        expireAllSessions(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawn(AccountEvent.Withdrawn event) {
        expireAllSessions(event.userId());
    }

    public void expireAllSessions(long userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserDetails user && user.getUserId() == userId) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }
}
