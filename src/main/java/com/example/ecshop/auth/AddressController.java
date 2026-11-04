package com.example.ecshop.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 住所の追加・変更・削除の画面。会員 ID は URL やフォームからは受け取らず、
 * 常にログイン中の会員（@AuthenticationPrincipal）から取る。
 */
@Controller
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** 住所フォームの都道府県の選択肢。このクラスのどの画面でも使えるようにモデルに入れる。 */
    @ModelAttribute("prefectures")
    public List<String> prefectures() {
        return Prefecture.labels();
    }

    @GetMapping("/account/addresses/new")
    public String newForm(Model model) {
        model.addAttribute("form", new AddressForm());
        return "account/address-form";
    }

    @PostMapping("/account/addresses")
    public String add(@AuthenticationPrincipal AppUserDetails user,
                      @Valid @ModelAttribute("form") AddressForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "account/address-form";
        }
        try {
            addressService.add(user.getUserId(), form);
        } catch (AddressService.AddressLimitExceededException e) {
            bindingResult.reject("limit",
                    "住所は" + AddressService.MAX_ADDRESSES + "件まで登録できます。不要な住所を削除してください。");
            return "account/address-form";
        }
        return "redirect:/account";
    }

    @GetMapping("/account/addresses/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id, Model model) {
        model.addAttribute("form", AddressForm.from(addressService.get(user.getUserId(), id)));
        model.addAttribute("addressId", id);
        return "account/address-form";
    }

    @PostMapping("/account/addresses/{id}")
    public String update(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id,
                         @Valid @ModelAttribute("form") AddressForm form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            // 入力エラーの画面を出す前にも持ち主を確かめる（他人の住所 ID なら 404）
            addressService.get(user.getUserId(), id);
            model.addAttribute("addressId", id);
            return "account/address-form";
        }
        addressService.update(user.getUserId(), id, form);
        return "redirect:/account";
    }

    @PostMapping("/account/addresses/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable long id) {
        addressService.delete(user.getUserId(), id);
        return "redirect:/account";
    }
}
