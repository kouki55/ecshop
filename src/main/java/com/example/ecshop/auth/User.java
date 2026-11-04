package com.example.ecshop.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * 会員（users テーブル）。
 * setter は置かず、「仮登録する」「有効化する」「ログイン失敗を記録する」など意味のある操作だけを公開する。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA が使うためのコンストラクタ。アプリからは register() を使う。 */
    protected User() {
    }

    /** 仮登録の会員を作る。passwordHash は PasswordEncoder でハッシュ化済みの値を渡す。 */
    public static User register(String email, String name, String passwordHash, Instant now) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.passwordHash = passwordHash;
        user.role = UserRole.MEMBER;
        user.status = UserStatus.PENDING;
        user.failedLoginCount = 0;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** 仮登録のまま再登録されたとき、名前とパスワードを新しい値に置き換える。 */
    public void updatePendingRegistration(String newName, String newPasswordHash, Instant now) {
        if (status != UserStatus.PENDING) {
            throw new IllegalStateException("仮登録の会員だけが対象");
        }
        this.name = newName;
        this.passwordHash = newPasswordHash;
        this.updatedAt = now;
    }

    /** メール確認が済んだので有効にする。 */
    public void activate(Instant now) {
        if (status != UserStatus.PENDING) {
            throw new IllegalStateException("仮登録の会員だけが対象");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now;
    }

    /** 氏名を変更する。 */
    public void changeName(String newName, Instant now) {
        this.name = newName;
        this.updatedAt = now;
    }

    /**
     * パスワードを変更する（パスワード再設定）。
     * メールを受け取れる本人だと確認できたので、失敗回数とロックも解除する。
     */
    public void changePassword(String newPasswordHash, Instant now) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("有効な会員だけが対象");
        }
        this.passwordHash = newPasswordHash;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /**
     * 退会する。行は消さずに残し（過去の注文が参照しているため）、個人情報は意味のない値に置き換える。
     * メールアドレスを置き換えるので、同じアドレスで新しく会員登録できるようになる。
     */
    public void withdraw(String anonymizedEmail, String anonymizedName, String unusablePasswordHash, Instant now) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("有効な会員だけが対象");
        }
        this.status = UserStatus.WITHDRAWN;
        this.email = anonymizedEmail;
        this.name = anonymizedName;
        this.passwordHash = unusablePasswordHash;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /** now の時点でロック中か。lockedUntil ちょうどの時刻からはロックが解除される。 */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * パスワード違いを 1 回記録する。maxAttempts 回連続で失敗したら lockDuration だけロックする。
     * 前回のロックが期限切れなら、回数を 0 から数え直す。
     */
    public void recordLoginFailure(Instant now, int maxAttempts, Duration lockDuration) {
        if (lockedUntil != null && !isLockedAt(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    /** ログインに成功したので、失敗回数とロックをリセットする。 */
    public void recordLoginSuccess(Instant now) {
        if (failedLoginCount != 0 || lockedUntil != null) {
            failedLoginCount = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
