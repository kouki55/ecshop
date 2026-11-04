package com.example.ecshop.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security に渡すログイン中の会員の情報。セッションに保存されるので、必要な値だけを持つ。
 * 会員 ID を持たせておき、今後の機能で「ログイン中の会員 ID を条件に取得する」ときに使う。
 */
public final class AppUserDetails implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final UserRole role;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private String passwordHash;

    public AppUserDetails(long userId, String email, String passwordHash, UserRole role, boolean enabled,
                          boolean accountNonLocked) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public long getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("ADMIN") は "ROLE_ADMIN" という権限名で判定されるので、ROLE_ を付ける
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** false だと Spring Security が DisabledException でログインを拒否する（仮登録・退会）。 */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** false だと Spring Security が LockedException でログインを拒否する（パスワードを照合する前に判定される）。 */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /** ログイン成功後に Spring Security が呼ぶ。セッションにパスワードのハッシュを残さない。 */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /**
     * 会員 ID が同じなら同じ会員とみなす。SessionRegistry は「会員ごとのセッション一覧」をこの判定で作るので、
     * 別のブラウザでログインした同じ会員のセッションが 1 つにまとまる。
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof AppUserDetails that && userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(userId);
    }

    @Override
    public String toString() {
        // 既定の toString だとログにメールアドレスなどが出るおそれがあるので、ID だけにする
        return "AppUserDetails[userId=" + userId + "]";
    }
}
