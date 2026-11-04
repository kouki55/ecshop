package com.example.ecshop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** パスワード再設定の依頼フォーム（メールアドレスだけ）。 */
public class PasswordResetRequestForm {

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 254, message = "メールアドレスは254文字以内で入力してください")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "PasswordResetRequestForm[email=***]";
    }
}
