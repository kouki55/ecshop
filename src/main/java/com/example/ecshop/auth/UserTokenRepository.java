package com.example.ecshop.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /**
     * まだ使われていなければ使用済みにする。戻り値は更新した行数。
     * 同じトークンで同時に 2 回送信されても、条件 usedAt is null により 1 件しか 1 を返さない。
     */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** 指定した会員・用途の未使用トークンをすべて使えなくする（新しいリンクを送り直すとき用）。 */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now"
            + " where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                         @Param("now") Instant now);
}
