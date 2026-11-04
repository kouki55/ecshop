package com.example.ecshop.common;

import com.example.ecshop.auth.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * トップページ。ログイン中ならメールアドレスとログアウトボタンを表示する。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUserDetails user, Model model) {
        // 未ログインなら user は null
        if (user != null) {
            model.addAttribute("loginEmail", user.getUsername());
        }
        return "index";
    }
}
