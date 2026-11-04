package com.example.ecshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新しいパスワードの入力フォーム。
 * パスワードの規則は「メールアドレスと同じでないか」の確認に会員の情報が必要なので、
 * ここではなく PasswordResetService で PasswordPolicy を使って検証する。
 */
public class PasswordResetForm {

    /** TokenGenerator が作る値（Base64URL で 43 文字）の形だけを受け付ける。 */
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String token;

    // 前後の空白も含めてそのまま受け取る（strip しない）
    private String password;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "PasswordResetForm[token=***, password=***]";
    }
}
