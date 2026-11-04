package com.example.ecshop.auth;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * パスワード再設定の画面。処理は PasswordResetService に任せる。
 */
@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password/forgot")
    public String forgotForm(Model model) {
        model.addAttribute("form", new PasswordResetRequestForm());
        return "auth/password-forgot";
    }

    @PostMapping("/password/forgot")
    public String forgot(@Valid @ModelAttribute("form") PasswordResetRequestForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/password-forgot";
        }
        passwordResetService.requestReset(form.getEmail());
        // 登録の有無にかかわらず、同じ画面へ送る
        return "redirect:/password/forgot/sent";
    }

    @GetMapping("/password/forgot/sent")
    public String forgotSent() {
        return "auth/password-forgot-sent";
    }

    /** メールのリンクから開く画面。ここではトークンを使用済みにしない（メールソフトの先読み対策）。 */
    @GetMapping("/password/reset")
    public String resetForm(@Valid @ModelAttribute("form") PasswordResetForm form, BindingResult bindingResult) {
        if (bindingResult.hasFieldErrors("token") || !passwordResetService.isUsable(form.getToken())) {
            return "auth/password-reset-invalid";
        }
        return "auth/password-reset";
    }

    @PostMapping("/password/reset")
    public String reset(@Valid @ModelAttribute("form") PasswordResetForm form, BindingResult bindingResult) {
        if (bindingResult.hasFieldErrors("token")) {
            return "auth/password-reset-invalid";
        }
        return switch (passwordResetService.resetPassword(form.getToken(), form.getPassword())) {
            case PasswordResetService.Result.Success success -> "redirect:/login?reset";
            case PasswordResetService.Result.InvalidToken invalid -> "auth/password-reset-invalid";
            case PasswordResetService.Result.PasswordRejected rejected -> {
                bindingResult.rejectValue("password", "rejected", rejected.message());
                yield "auth/password-reset";
            }
        };
    }
}
