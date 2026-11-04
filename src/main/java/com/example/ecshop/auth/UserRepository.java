package com.example.ecshop.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // 自動生成の findByEmailIgnoreCase は upper() を使うため、V1 の lower(email) の一意インデックスが使われない。
    // lower() で書いて、インデックスと同じ式にそろえる。:email はパラメータとしてバインドされる（文字列連結しない）
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * 行ロック（SELECT ... FOR UPDATE）をかけて取得する。トランザクションが終わるまで、
     * 同じ会員への更新は待たされるので、並列にログイン失敗しても回数の数え漏れが起きない。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
