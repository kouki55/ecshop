package com.example.ecshop.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 会員登録のメールを送る（ローカルでは Mailpit に届く）。
 * AFTER_COMMIT にすることで、DB への保存が確定したときだけ送る。
 * 保存に失敗したのにメールだけ届く（開いても無効なリンク）ということが起きない。
 * @Async で別スレッドで送るので、画面の応答時間がメール送信に左右されない。
 */
@Component
public class RegistrationMailSender {

    private static final Logger log = LoggerFactory.getLogger(RegistrationMailSender.class);

    private final MailSender mailSender;
    private final AuthProperties properties;

    public RegistrationMailSender(MailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationRequested(RegistrationMailEvent.VerificationRequested event) {
        String link = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/register/verify")
                .queryParam("token", event.token())
                .build()
                .toUriString();
        send(event, "【ecshop】メールアドレスの確認", lines(
                event.name() + " 様",
                "",
                "ecshop への会員登録ありがとうございます。",
                "次のリンクを開き、表示された画面で「登録を完了する」を押してください。",
                "",
                link,
                "",
                "このリンクの有効期限は " + properties.verificationTokenTtl().toHours() + " 時間です。",
                "お心当たりのない場合は、このメールを破棄してください。"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlreadyRegistered(RegistrationMailEvent.AlreadyRegistered event) {
        send(event, "【ecshop】会員登録のお申し込みについて", lines(
                event.name() + " 様",
                "",
                "このメールアドレスは、すでに ecshop に登録されています。",
                "ログイン画面からログインしてください。",
                "",
                properties.baseUrl() + "/login",
                "",
                "お心当たりのない場合は、このメールを破棄してください。"));
    }

    /** 1 行ずつ並べてメール本文にする。 */
    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private void send(RegistrationMailEvent event, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(event.email());
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 登録はもう確定しているので、利用者には同じ画面を返す（再登録すればリンクを送り直せる）。
            // 例外メッセージには宛先が含まれることがあるので、ログには種類だけを出す
            log.warn("会員登録のメールを送信できませんでした: {}", e.getClass().getName());
        }
    }
}
