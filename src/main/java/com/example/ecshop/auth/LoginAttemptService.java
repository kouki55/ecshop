package com.example.ecshop.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン失敗の回数を数え、5 回連続で失敗したアカウントを 15 分ロックする。
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final Clock clock;

    public LoginAttemptService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** パスワード違いを記録する。存在しないメールアドレスなら何もしない。 */
    @Transactional
    public void recordFailure(String email) {
        // 行ロックで取得するので、同じ会員への並列の失敗も 1 回ずつ確実に数えられる
        userRepository.findByEmailIgnoreCaseForUpdate(email)
                .ifPresent(user -> user.recordLoginFailure(clock.instant(), MAX_FAILED_ATTEMPTS, LOCK_DURATION));
    }

    /** ログイン成功を記録し、失敗回数を 0 に戻す。 */
    @Transactional
    public void recordSuccess(long userId) {
        userRepository.findByIdForUpdate(userId).ifPresent(user -> user.recordLoginSuccess(clock.instant()));
    }
}
