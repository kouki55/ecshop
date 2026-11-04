package com.example.ecshop.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 47 都道府県のどれかであることを検証する。画面は選択肢だが、送信内容は書き換えられるのでサーバー側でも確かめる。
 * 空欄（null）は @NotBlank に任せ、ここでは通す。
 */
@Documented
@Constraint(validatedBy = ValidPrefecture.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPrefecture {

    String message() default "都道府県を選択してください";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPrefecture, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Prefecture.isValidLabel(value);
        }
    }
}
