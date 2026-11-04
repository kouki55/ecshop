package com.example.ecshop.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * カートの明細。会員から操作される取得は、必ずログイン中の会員 ID（cart.userId）を条件に含める。
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** 自分のカートの明細だけを取得する。他人の明細 ID を指定しても見つからない。 */
    @EntityGraph(attributePaths = "product")
    Optional<CartItem> findByIdAndCartUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "product")
    List<CartItem> findByCartUserIdOrderByIdAsc(Long userId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    long countByCartId(Long cartId);

    // :userId はパラメータとしてバインドされる（文字列連結で SQL を組まない）
    @Modifying
    @Query("delete from CartItem i where i.cart.id in (select c.id from Cart c where c.userId = :userId)")
    void deleteByUserId(@Param("userId") Long userId);
}
