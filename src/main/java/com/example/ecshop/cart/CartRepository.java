package com.example.ecshop.cart;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    @Modifying
    @Query("delete from Cart c where c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
