package com.example.ecshop.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 登録情報の画面。URL に会員 ID を含めず、誰の情報かは常にログイン中の会員から決める。
 */
@Controller
public class AccountController {

    private final AccountService accountService;
    private final AddressService addressService;

    public AccountController(AccountService accountService, AddressService addressService) {
        this.accountService = accountService;
        this.addressService = addressService;
    }

    @GetMapping("/account")
    public String show(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("user", accountService.get(user.getUserId()));
        model.addAttribute("addresses", addressService.list(user.getUserId()));
        model.addAttribute("maxAddresses", AddressService.MAX_ADDRESSES);
        return "account/index";
    }

    @GetMapping("/account/profile/edit")
    public String editProfile(@AuthenticationPrincipal AppUserDetails user, Model model) {
        ProfileForm form = new ProfileForm();
        form.setName(accountService.get(user.getUserId()).getName());
        model.addAttribute("form", form);
        return "account/profile-edit";
    }

    @PostMapping("/account/profile")
    public String updateProfile(@AuthenticationPrincipal AppUserDetails user,
                                @Valid @ModelAttribute("form") ProfileForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "account/profile-edit";
        }
        accountService.changeName(user.getUserId(), form.getName());
        return "redirect:/account";
    }
}
