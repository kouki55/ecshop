package com.example.ecshop.order;

import com.example.ecshop.auth.AddressService;
import com.example.ecshop.auth.AppUserDetails;
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
 * 注文の画面（配送先の選択 → 確認 → 確定・支払い → 完了）。
 * 会員 ID は URL やフォームから受け取らず、常にログイン中の会員から取る。
 * 確定・支払いのあとはリダイレクトする（再読み込みで同じ操作がくり返されないように）。
 */
@Controller
public class OrderController {

    private static final String PAYMENT_FAILED = "お支払いができませんでした。別の支払い方法をお試しください。";
    private static final String CART_NOT_READY = "カートの内容を確認してください。購入できない商品が含まれているか、カートが空です。";

    private final OrderService orderService;
    private final AddressService addressService;
    private final OrderProperties properties;

    public OrderController(OrderService orderService, AddressService addressService, OrderProperties properties) {
        this.orderService = orderService;
        this.addressService = addressService;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- ① 配送先

    @GetMapping("/orders/new")
    public String shipping(@AuthenticationPrincipal AppUserDetails user, Model model, RedirectAttributes redirect) {
        if (!orderService.isCartReady(user.getUserId())) {
            redirect.addFlashAttribute("cartError", CART_NOT_READY);
            return "redirect:/cart";
        }
        model.addAttribute("addresses", addressService.list(user.getUserId()));
        return "order/shipping";
    }

    // ---------------------------------------------------------------- ② 確認

    /** 表示するだけで何も変えないので GET にしている（配送先の ID は URL のパラメータで受け取る）。 */
    @GetMapping("/orders/confirm")
    public String confirm(@AuthenticationPrincipal AppUserDetails user, @Valid @ModelAttribute ConfirmForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("orderError", "配送先を選んでください。");
            return "redirect:/orders/new";
        }
        OrderService.ConfirmResult result = orderService.confirm(user.getUserId(), form.getAddressId());
        if (result instanceof OrderService.ConfirmResult.Ready ready) {
            model.addAttribute("confirm", ready.view());
            model.addAttribute("paymentTimeoutMinutes", properties.paymentTimeout().toMinutes());
            return "order/confirm";
        }
        redirect.addFlashAttribute("cartError", CART_NOT_READY);
        return "redirect:/cart";
    }

    // ---------------------------------------------------------------- ③ 確定・支払い

    @PostMapping("/orders")
    public String place(@AuthenticationPrincipal AppUserDetails user, @Valid @ModelAttribute PlaceOrderForm form,
                        BindingResult bindingResult, RedirectAttributes redirect) {
        // 支払い方法以外は確認画面が埋め込んだ hidden 項目。不正なら書き換えられたリクエストなので 400
        if (bindingResult.hasFieldErrors("idempotencyKey") || bindingResult.hasFieldErrors("addressId")
                || bindingResult.hasFieldErrors("expectedTotal")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (bindingResult.hasErrors()) {
            return backToConfirm(form.getAddressId(), "支払い方法を選んでください。", redirect);
        }
        return switch (orderService.place(user.getUserId(), form)) {
            case OrderService.PlaceResult.Paid paid -> toComplete(paid.orderId(), redirect);
            case OrderService.PlaceResult.PaymentFailed failed -> {
                redirect.addFlashAttribute("orderError", PAYMENT_FAILED);
                yield toPayment(failed.orderId(), redirect);
            }
            // 二重送信。新しい注文は作らず、先に作られた注文の画面を出す
            case OrderService.PlaceResult.AlreadyPlaced placed ->
                    redirectByStatus(placed.orderId(), placed.status(), redirect);
            case OrderService.PlaceResult.CartNotReady notReady -> {
                redirect.addFlashAttribute("cartError", CART_NOT_READY);
                yield "redirect:/cart";
            }
            case OrderService.PlaceResult.PriceChanged changed -> backToConfirm(form.getAddressId(),
                    "ご注文の金額が変わりました。内容をご確認のうえ、もう一度確定してください。", redirect);
        };
    }

    @GetMapping("/orders/{id}/payment")
    public String payment(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id, Model model,
                          RedirectAttributes redirect) {
        OrderView order = orderService.get(user.getUserId(), id);
        if (order.status() != OrderStatus.RECEIVED && order.status() != OrderStatus.CANCELLED) {
            return toComplete(id, redirect);
        }
        model.addAttribute("order", order);
        model.addAttribute("paymentMethods", orderService.paymentMethods());
        return "order/payment";
    }

    @PostMapping("/orders/{id}/payment")
    public String pay(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id,
                      @Valid @ModelAttribute PayForm form, BindingResult bindingResult,
                      RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            // エラーを返す前にも持ち主を確かめる（他人の注文 ID なら 404）
            orderService.get(user.getUserId(), id);
            redirect.addFlashAttribute("orderError", "支払い方法を選んでください。");
            return toPayment(id, redirect);
        }
        return switch (orderService.pay(user.getUserId(), id, form.getPaymentToken())) {
            case OrderService.PayResult.Paid paid -> toComplete(id, redirect);
            case OrderService.PayResult.Failed failed -> {
                redirect.addFlashAttribute("orderError", PAYMENT_FAILED);
                yield toPayment(id, redirect);
            }
            case OrderService.PayResult.NotPayable notPayable -> redirectByStatus(id, notPayable.status(), redirect);
        };
    }

    // ---------------------------------------------------------------- ④ 完了

    @GetMapping("/orders/{id}/complete")
    public String complete(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id, Model model,
                           RedirectAttributes redirect) {
        OrderView order = orderService.get(user.getUserId(), id);
        if (order.status() == OrderStatus.RECEIVED || order.status() == OrderStatus.CANCELLED) {
            // まだ支払われていない（またはキャンセルされた）注文に「完了」は出さない
            return toPayment(id, redirect);
        }
        model.addAttribute("order", order);
        return "order/complete";
    }

    // ---------------------------------------------------------------- リダイレクト先

    /** 受付・キャンセルなら支払い画面、それ以外（支払済など）なら完了画面。 */
    private static String redirectByStatus(long orderId, OrderStatus status, RedirectAttributes redirect) {
        boolean unpaid = status == OrderStatus.RECEIVED || status == OrderStatus.CANCELLED;
        return unpaid ? toPayment(orderId, redirect) : toComplete(orderId, redirect);
    }

    private static String toPayment(long orderId, RedirectAttributes redirect) {
        // URL は文字列連結で作らず、テンプレート（{id}）に値を埋め込む
        redirect.addAttribute("id", orderId);
        return "redirect:/orders/{id}/payment";
    }

    private static String toComplete(long orderId, RedirectAttributes redirect) {
        redirect.addAttribute("id", orderId);
        return "redirect:/orders/{id}/complete";
    }

    private static String backToConfirm(Long addressId, String error, RedirectAttributes redirect) {
        redirect.addFlashAttribute("orderError", error);
        redirect.addAttribute("addressId", addressId);
        return "redirect:/orders/confirm";
    }
}
