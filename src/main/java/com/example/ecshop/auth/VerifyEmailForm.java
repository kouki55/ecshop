package com.example.ecshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * メール確認リンクのトークン。TokenGenerator が作る値（Base64URL で 43 文字）の形だけを受け付ける。
 */
public class VerifyEmailForm {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "VerifyEmailForm[token=***]";
    }
}
