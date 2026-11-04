package com.example.ecshop.cart;

import com.example.ecshop.auth.AccountEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 退会したら、その会員のカートを削除する。
 *
 * <p>auth パッケージから cart を直接呼ぶと、cart → auth（会員のロック）と合わせてパッケージが
 * 互いに依存し合うので、退会イベントを受け取る形にしている。
 * {@code @EventListener} はイベントを発行したその場（退会と同じトランザクションの中）で動くので、
 * 削除に失敗すれば退会も取り消される。
 */
@Component
public class CartCleanupListener {

    private final CartService cartService;

    public CartCleanupListener(CartService cartService) {
        this.cartService = cartService;
    }

    @EventListener
    public void onWithdrawn(AccountEvent.Withdrawn event) {
        cartService.deleteCart(event.userId());
    }
}
