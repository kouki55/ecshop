package com.example.ecshop.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * フォームの password が PasswordPolicy を満たすかを検証する Bean Validation の独自アノテーション。
 * メールアドレスとの比較が必要なので、フィールドではなくフォームのクラスに付ける。
 * エラーは password 項目のエラーとして表示される。
 */
@Documented
@Constraint(validatedBy = AcceptablePasswordValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AcceptablePassword {

    String message() default "パスワードが規則を満たしていません";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
