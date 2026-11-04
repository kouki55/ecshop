package com.example.ecshop.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 住所の取得は、必ずログイン中の会員 ID（userId）を条件に含める。
 * findById(id) だけで取ると、URL の ID を書き換えた他人の住所まで取れてしまうので使わない。
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Query("delete from Address a where a.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
