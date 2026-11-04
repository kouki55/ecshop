package com.example.ecshop.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * パスワード再設定（ワンタイムリンクをメールで送り、そのリンクから新しいパスワードを設定する）。
 */
@Service
public class PasswordResetService {

    /** resetPassword の結果。sealed なので、呼び出し側の switch で全パターンを扱ったかコンパイラが確認できる。 */
    public sealed interface Result {
        /** 変更できた。 */
        record Success() implements Result {
        }

        /** リンクが存在しない・期限切れ・使用済み（理由は利用者に区別させない）。 */
        record InvalidToken() implements Result {
        }

        /** 新しいパスワードが規則に合わない。リンクはまだ使える。 */
        record PasswordRejected(String message) implements Result {
        }
    }

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository, UserTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                TokenGenerator tokenGenerator, ApplicationEventPublisher events,
                                AuthProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenGenerator = tokenGenerator;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 再設定リンクを送る。メールを送るのは有効な会員だけで、未登録・仮登録・退会済みなら何もしない。
     * どの場合も例外を投げず同じように終わるので、画面からは登録の有無が分からない。
     */
    @Transactional
    public void requestReset(String email) {
        Instant now = clock.instant();
        Optional<User> found = userRepository.findByEmailIgnoreCaseForUpdate(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        // 新しいリンクを送ったら、それより前のリンクは使えなくする
        tokenRepository.invalidateUnused(user.getId(), TokenPurpose.PASSWORD_RESET, now);
        String token = tokenGenerator.generate();
        tokenRepository.save(UserToken.issue(user.getId(), TokenPurpose.PASSWORD_RESET,
                tokenGenerator.hash(token), now, now.plus(properties.passwordResetTokenTtl())));
        events.publishEvent(new AccountEvent.PasswordResetRequested(user.getEmail(), user.getName(), token));
    }

    /** リンクを開いたときに、入力画面を出してよいかを確かめる（ここではトークンを使用済みにしない）。 */
    @Transactional(readOnly = true)
    public boolean isUsable(String token) {
        return findUsableToken(token, clock.instant()).isPresent();
    }

    @Transactional
    public Result resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        Optional<UserToken> found = findUsableToken(token, now);
        if (found.isEmpty()) {
            return new Result.InvalidToken();
        }
        User user = userRepository.findByIdForUpdate(found.get().getUserId()).orElseThrow();
        if (user.getStatus() != UserStatus.ACTIVE) {
            return new Result.InvalidToken();
        }
        // 規則に合わないときはトークンを消費せず、同じリンクで入力し直せるようにする
        Optional<String> violation = passwordPolicy.validate(newPassword, user.getEmail());
        if (violation.isPresent()) {
            return new Result.PasswordRejected(violation.get());
        }
        // 同じリンクが同時に 2 回送信されても、使用済みにできるのは 1 回だけ
        if (tokenRepository.markUsed(found.get().getId(), now) != 1) {
            return new Result.InvalidToken();
        }
        user.changePassword(passwordEncoder.encode(newPassword), now);
        tokenRepository.invalidateUnused(user.getId(), TokenPurpose.PASSWORD_RESET, now);
        events.publishEvent(new AccountEvent.PasswordChanged(user.getId(), user.getEmail(), user.getName()));
        return new Result.Success();
    }

    private Optional<UserToken> findUsableToken(String token, Instant now) {
        return tokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(token), TokenPurpose.PASSWORD_RESET)
                .filter(t -> t.isUsableAt(now));
    }
}
