package com.example.ecshop.auth;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 会員登録とメール確認の画面。入力の受け取りと画面の切り替えだけを行い、処理は RegistrationService に任せる。
 */
@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register")
    public String form(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        registrationService.register(form);
        // 送信後はリダイレクトする（再読み込みで二重登録されないように。PRG パターン）
        return "redirect:/register/sent";
    }

    @GetMapping("/register/sent")
    public String sent() {
        return "auth/register-sent";
    }

    /**
     * メールのリンクから開く画面。ここではまだ有効化しない。
     * メールソフトやセキュリティ製品がリンクを先読みしても登録が完了しないよう、ボタン（POST）で完了させる。
     */
    @GetMapping("/register/verify")
    public String confirm(@Valid @ModelAttribute("form") VerifyEmailForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/verify-invalid";
        }
        return "auth/verify-confirm";
    }

    @PostMapping("/register/verify")
    public String verify(@Valid @ModelAttribute("form") VerifyEmailForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors() || !registrationService.verifyEmail(form.getToken())) {
            return "auth/verify-invalid";
        }
        return "auth/verify-done";
    }
}
