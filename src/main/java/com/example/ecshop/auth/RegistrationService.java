package com.example.ecshop.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員登録（仮登録）とメール確認による有効化。
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;

    public RegistrationService(UserRepository userRepository, UserTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder, TokenGenerator tokenGenerator,
                               ApplicationEventPublisher events, AuthProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 仮登録して確認メールを送る。メールアドレスが登録済みかどうかを画面で区別させないため、
     * どの場合も例外を投げずに終わり、違いはメールの内容だけにする。
     * <ul>
     *   <li>未登録：仮登録の会員を作り、確認リンクを送る</li>
     *   <li>仮登録のまま：名前とパスワードを上書きし、古いリンクを無効にして新しいリンクを送る
     *       （他人に先に仮登録されても、メールを受け取れる本人が取り戻せる）</li>
     *   <li>登録済み（有効・退会）：「すでに登録されています」という通知だけを送る</li>
     * </ul>
     */
    @Transactional
    public void register(RegistrationForm form) {
        Instant now = clock.instant();
        // 登録済みかどうかで処理時間に差が出ないよう、先に必ずハッシュ化する（bcrypt は意図的に遅い）
        String passwordHash = passwordEncoder.encode(form.getPassword());
        Optional<User> existing = userRepository.findByEmailIgnoreCaseForUpdate(form.getEmail());

        if (existing.isEmpty()) {
            User user = userRepository.save(User.register(form.getEmail(), form.getName(), passwordHash, now));
            sendVerification(user, now);
            return;
        }

        User user = existing.get();
        if (user.getStatus() == UserStatus.PENDING) {
            user.updatePendingRegistration(form.getName(), passwordHash, now);
            tokenRepository.invalidateUnused(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now);
            sendVerification(user, now);
        } else {
            events.publishEvent(new RegistrationMailEvent.AlreadyRegistered(user.getEmail(), user.getName()));
        }
    }

    /**
     * メールのリンクのトークンで会員を有効化する。成功したら true。
     * トークンが存在しない・期限切れ・使用済みの場合は false（理由は利用者に区別させない）。
     */
    @Transactional
    public boolean verifyEmail(String token) {
        Instant now = clock.instant();
        Optional<UserToken> found = tokenRepository.findByTokenHashAndPurpose(
                tokenGenerator.hash(token), TokenPurpose.EMAIL_VERIFICATION);
        if (found.isEmpty() || !found.get().isUsableAt(now)) {
            return false;
        }
        // 同じリンクが同時に 2 回送信されても、使用済みにできるのは 1 回だけ
        if (tokenRepository.markUsed(found.get().getId(), now) != 1) {
            return false;
        }
        User user = userRepository.findByIdForUpdate(found.get().getUserId()).orElseThrow();
        if (user.getStatus() != UserStatus.PENDING) {
            return false;
        }
        user.activate(now);
        return true;
    }

    private void sendVerification(User user, Instant now) {
        String token = tokenGenerator.generate();
        tokenRepository.save(UserToken.issue(user.getId(), TokenPurpose.EMAIL_VERIFICATION,
                tokenGenerator.hash(token), now, now.plus(properties.verificationTokenTtl())));
        events.publishEvent(new RegistrationMailEvent.VerificationRequested(user.getEmail(), user.getName(), token));
    }
}
