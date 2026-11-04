package com.example.ecshop.auth;

import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン時に Spring Security から呼ばれ、メールアドレスで会員を探す。
 * パスワードの照合は Spring Security（DaoAuthenticationProvider）が PasswordEncoder で行う。
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public AppUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                // この例外は Spring Security がパスワード違いと同じ扱いにするので、存在しないことは利用者に伝わらない
                .orElseThrow(() -> new UsernameNotFoundException("会員が見つかりません"));
        return new AppUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                user.getStatus() == UserStatus.ACTIVE,
                !user.isLockedAt(clock.instant()));
    }
}
