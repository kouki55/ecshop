package com.example.ecshop.cart;

import com.example.ecshop.auth.AppUserDetails;
import com.example.ecshop.pricing.PriceCalculator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * カート画面。会員 ID は URL やフォームからは受け取らず、常にログイン中の会員から取る。
 * 追加・変更・削除のあとはリダイレクトする（再読み込みで同じ操作がくり返されないように）。
 * 結果のメッセージはフラッシュ属性（リダイレクト先で 1 回だけ表示される値）で渡す。
 */
@Controller
public class CartController {

    private static final String QUANTITY_MESSAGE = "数量は1〜" + CartItem.MAX_QUANTITY + "で指定してください。";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/cart")
    public String show(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("cart", cartService.view(user.getUserId()));
        model.addAttribute("freeShippingThreshold", PriceCalculator.FREE_SHIPPING_THRESHOLD);
        return "cart/index";
    }

    @PostMapping("/cart/items")
    public String add(@AuthenticationPrincipal AppUserDetails user, @Valid @ModelAttribute AddToCartForm form,
                      BindingResult bindingResult, RedirectAttributes redirect) {
        // 商品 ID は画面の hidden 項目で、利用者が入力する値ではない。不正なら書き換えられたリクエストなので 400
        if (bindingResult.hasFieldErrors("productId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (bindingResult.hasErrors()) {
            // 入力値の変換エラーの既定メッセージには内部の型名などが含まれるので、決まった文言だけを出す
            return backToProduct(form.getProductId(), QUANTITY_MESSAGE, redirect);
        }
        CartService.Result result = cartService.add(user.getUserId(), form.getProductId(), form.getQuantity());
        if (result instanceof CartService.Result.Success) {
            redirect.addFlashAttribute("cartMessage", "カートに追加しました。");
            return "redirect:/cart";
        }
        return backToProduct(form.getProductId(), errorMessage(result), redirect);
    }

    /** 商品詳細画面に戻してエラーを表示する。 */
    private static String backToProduct(long productId, String error, RedirectAttributes redirect) {
        redirect.addFlashAttribute("cartError", error);
        // 戻り先の URL は文字列連結で作らず、テンプレート（{id}）に値を埋め込む
        redirect.addAttribute("id", productId);
        return "redirect:/products/{id}";
    }

    @PostMapping("/cart/items/{itemId}")
    public String changeQuantity(@AuthenticationPrincipal AppUserDetails user, @PathVariable long itemId,
                                 @Valid @ModelAttribute ChangeQuantityForm form, BindingResult bindingResult,
                                 RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            // エラーを返す前にも持ち主を確かめる（他人の明細 ID なら 404）
            cartService.checkOwned(user.getUserId(), itemId);
            redirect.addFlashAttribute("cartError", QUANTITY_MESSAGE);
            return "redirect:/cart";
        }
        CartService.Result result = cartService.changeQuantity(user.getUserId(), itemId, form.getQuantity());
        if (result instanceof CartService.Result.Success) {
            redirect.addFlashAttribute("cartMessage", "数量を変更しました。");
        } else {
            redirect.addFlashAttribute("cartError", errorMessage(result));
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{itemId}/delete")
    public String remove(@AuthenticationPrincipal AppUserDetails user, @PathVariable long itemId,
                         RedirectAttributes redirect) {
        cartService.remove(user.getUserId(), itemId);
        redirect.addFlashAttribute("cartMessage", "カートから削除しました。");
        return "redirect:/cart";
    }

    private static String errorMessage(CartService.Result result) {
        return switch (result) {
            case CartService.Result.Success success -> "";
            // 残りの個数は出さない（商品画面と同じく、正確な在庫数は見せない）
            case CartService.Result.InsufficientStock insufficient -> "在庫が足りません。数量を減らしてください。";
            case CartService.Result.QuantityLimitExceeded exceeded ->
                    "1つの商品は" + CartItem.MAX_QUANTITY + "個までカートに入れられます。";
            case CartService.Result.ItemLimitExceeded exceeded ->
                    "カートに入れられる商品は" + CartService.MAX_ITEMS + "種類までです。";
            case CartService.Result.Discontinued discontinued -> "この商品は販売を終了しました。";
        };
    }
}
