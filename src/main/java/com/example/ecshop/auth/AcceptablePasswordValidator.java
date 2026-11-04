package com.example.ecshop.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

/**
 * {@link AcceptablePassword} の検証処理。規則そのものは PasswordPolicy にまとめ、ここはつなぐだけ。
 * Spring が作る Validator を通すと、コンストラクタに Spring の Bean（PasswordPolicy）が注入される。
 */
public class AcceptablePasswordValidator implements ConstraintValidator<AcceptablePassword, RegistrationForm> {

    private final PasswordPolicy passwordPolicy;

    public AcceptablePasswordValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    public boolean isValid(RegistrationForm form, ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }
        Optional<String> violation = passwordPolicy.validate(form.getPassword(), form.getEmail());
        if (violation.isEmpty()) {
            return true;
        }
        // 既定の「クラス全体のエラー」をやめ、password 項目のエラーとして登録する
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(escapeForTemplate(violation.get()))
                .addPropertyNode("password")
                .addConstraintViolation();
        return false;
    }

    /** メッセージテンプレートでは { } $ \ が特別な意味を持つので、文字として扱われるようにする。 */
    private static String escapeForTemplate(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
