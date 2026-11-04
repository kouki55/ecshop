package com.example.ecshop.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退会。会員の行は残し（過去の注文が参照しているため）、個人情報は匿名化・削除する。
 */
@Service
public class WithdrawalService {

    static final String ANONYMIZED_NAME = "退会済み会員";

    public sealed interface Result {
        record Success() implements Result {
        }

        /** パスワードが違う。ログイン失敗と同じく回数を数える。 */
        record WrongPassword() implements Result {
        }

        /** パスワードの失敗が続いてロック中。 */
        record Locked() implements Result {
        }
    }

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WithdrawalService(UserRepository userRepository, UserTokenRepository tokenRepository,
                             AddressRepository addressRepository, PasswordEncoder passwordEncoder,
                             TokenGenerator tokenGenerator, ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result withdraw(long userId, String password) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        if (user.isLockedAt(now)) {
            return new Result.Locked();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // この画面でパスワードを総当たりされないよう、ログイン失敗と同じ回数に数える
            user.recordLoginFailure(now, LoginAttemptService.MAX_FAILED_ATTEMPTS, LoginAttemptService.LOCK_DURATION);
            return new Result.WrongPassword();
        }

        // 匿名化する前の値（完了メールの宛先）を控えておく
        String originalEmail = user.getEmail();
        String originalName = user.getName();

        addressRepository.deleteByUserId(userId);
        tokenRepository.invalidateUnused(userId, TokenPurpose.EMAIL_VERIFICATION, now);
        tokenRepository.invalidateUnused(userId, TokenPurpose.PASSWORD_RESET, now);
        user.withdraw(
                // .invalid は「実在しないドメイン」として予約されている（RFC 2606）。ID を入れて一意にする
                "withdrawn-" + userId + "@withdrawn.invalid",
                ANONYMIZED_NAME,
                // 誰も知らないランダムな値のハッシュに置き換え、元のパスワードでは照合できないようにする
                passwordEncoder.encode(tokenGenerator.generate()),
                now);
        events.publishEvent(new AccountEvent.Withdrawn(userId, originalEmail, originalName));
        return new Result.Success();
    }
}
