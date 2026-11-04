package com.example.ecshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 氏名の変更フォーム。変えられるのは氏名だけ。 */
public class ProfileForm {

    @NotBlank(message = "お名前を入力してください")
    @Size(max = 100, message = "お名前は100文字以内で入力してください")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    @Override
    public String toString() {
        return "ProfileForm[name=***]";
    }
}
