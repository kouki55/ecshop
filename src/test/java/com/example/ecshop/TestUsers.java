package com.example.ecshop;

import com.example.ecshop.auth.Address;
import com.example.ecshop.auth.AddressForm;
import com.example.ecshop.auth.AddressRepository;
import com.example.ecshop.auth.AppUserDetails;
import com.example.ecshop.auth.User;
import com.example.ecshop.auth.UserRepository;
import com.example.ecshop.auth.UserStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * テスト用の会員を作る。テスト同士でぶつからないよう、メールアドレスは毎回ランダムにする。
 */
public class TestUsers {

    /** 漏えいリストに含まれず、規則を満たすパスワード。 */
    public static final String PASSWORD = "wiper-blade-and-engine-oil";

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private String passwordHash;

    public TestUsers(UserRepository userRepository, AddressRepository addressRepository,
                     PasswordEncoder passwordEncoder, JdbcTemplate jdbc, MutableClock clock) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * MockMvc の with(user(...)) に渡すログイン中の会員。
     * 本物のログイン（bcrypt で約 0.7 秒かかる）をせずに、その会員としてリクエストを送れる。
     */
    public static AppUserDetails principal(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), null, user.getRole(),
                user.getStatus() == UserStatus.ACTIVE, true);
    }

    public User pending() {
        return userRepository.save(User.register(randomEmail(), "テスト会員", passwordHash(), clock.instant()));
    }

    /** bcrypt は意図的に遅い（このマシンで 1 回約 0.7 秒）ので、ハッシュは 1 回だけ作って使い回す。 */
    private synchronized String passwordHash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    public User active() {
        User user = pending();
        user.activate(clock.instant());
        return userRepository.save(user);
    }

    /** 状態だけが退会済みの会員（退会の処理そのものは WithdrawalTest で確認する）。 */
    public User withdrawn() {
        User user = active();
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", user.getId());
        return reload(user);
    }

    /** 5 回失敗した直後と同じ状態（15 分ロック）にする。 */
    public User locked(User user) {
        jdbc.update("UPDATE users SET failed_login_count = 5, locked_until = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(15))), user.getId());
        return reload(user);
    }

    public Address address(User owner, String recipientName) {
        AddressForm form = new AddressForm();
        form.setRecipientName(recipientName);
        form.setPostalCode("1000001");
        form.setPrefecture("東京都");
        form.setCity("千代田区");
        form.setAddressLine1("千代田1-1");
        form.setPhone("0312345678");
        return addressRepository.save(Address.create(owner.getId(), form, clock.instant()));
    }

    public User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }
}
