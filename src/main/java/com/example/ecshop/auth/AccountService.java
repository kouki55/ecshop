package com.example.ecshop.auth;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登録情報（氏名など）の閲覧と変更。対象は常にログイン中の会員自身。
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final Clock clock;

    public AccountService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(long userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    @Transactional
    public void changeName(long userId, String newName) {
        userRepository.findByIdForUpdate(userId).orElseThrow().changeName(newName, clock.instant());
    }
}
