package com.example.ecshop.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 退会の画面。処理は WithdrawalService に任せ、ここでは結果に応じた画面の切り替えとログアウトだけを行う。
 */
@Controller
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/account/withdraw")
    public String confirm(Model model) {
        model.addAttribute("form", new WithdrawForm());
        return "account/withdraw";
    }

    @PostMapping("/account/withdraw")
    public String withdraw(@AuthenticationPrincipal AppUserDetails user,
                           @Valid @ModelAttribute("form") WithdrawForm form, BindingResult bindingResult,
                           Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return "account/withdraw";
        }
        return switch (withdrawalService.withdraw(user.getUserId(), form.getPassword())) {
            case WithdrawalService.Result.Success success -> {
                // 今のセッションを破棄してログアウトする（ほかのセッションは UserSessionService が失効させる）
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                yield "redirect:/withdrawn";
            }
            case WithdrawalService.Result.WrongPassword wrong -> {
                bindingResult.rejectValue("password", "wrong", "パスワードが正しくありません");
                yield "account/withdraw";
            }
            case WithdrawalService.Result.Locked locked -> {
                bindingResult.reject("locked", "パスワードの間違いが続いたため、しばらく時間をおいてからお試しください");
                yield "account/withdraw";
            }
        };
    }

    @GetMapping("/withdrawn")
    public String withdrawn() {
        return "account/withdrawn";
    }
}
