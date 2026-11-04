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
 * パスワード再設定・退会のメールを送る（ローカルでは Mailpit に届く）。
 * <ul>
 *   <li>AFTER_COMMIT：DB への保存が確定したときだけ送る</li>
 *   <li>@Async：別スレッドで送るので、画面の応答時間がメール送信に左右されない。
 *       再設定の依頼で「登録済みのときだけ遅い」という差が出ず、登録の有無が分からない</li>
 * </ul>
 */
@Component
public class AccountMailSender {

    private static final Logger log = LoggerFactory.getLogger(AccountMailSender.class);

    private final MailSender mailSender;
    private final AuthProperties properties;

    public AccountMailSender(MailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(AccountEvent.PasswordResetRequested event) {
        String link = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/password/reset")
                .queryParam("token", event.token())
                .build()
                .toUriString();
        send(event.email(), "【ecshop】パスワードの再設定", lines(
                event.name() + " 様",
                "",
                "パスワード再設定のお申し込みを受け付けました。",
                "次のリンクを開き、新しいパスワードを設定してください。",
                "",
                link,
                "",
                "このリンクの有効期限は " + properties.passwordResetTokenTtl().toMinutes() + " 分で、1 回だけ使えます。",
                "お心当たりのない場合は、このメールを破棄してください。パスワードは変更されません。"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(AccountEvent.PasswordChanged event) {
        send(event.email(), "【ecshop】パスワードが変更されました", lines(
                event.name() + " 様",
                "",
                "ecshop のパスワードが変更されました。",
                "ほかの端末でのログインは、安全のためすべてログアウトしました。",
                "",
                "お心当たりのない場合は、すぐに次のページからパスワードを再設定してください。",
                properties.baseUrl() + "/password/forgot"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawn(AccountEvent.Withdrawn event) {
        send(event.email(), "【ecshop】退会手続きが完了しました", lines(
                event.name() + " 様",
                "",
                "ecshop の退会手続きが完了しました。これまでのご利用ありがとうございました。",
                "登録されていたお名前・住所の情報は削除しました。"));
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 例外メッセージには宛先が含まれることがあるので、ログには種類だけを出す
            log.warn("メールを送信できませんでした: {}", e.getClass().getName());
        }
    }

    /** 1 行ずつ並べてメール本文にする。 */
    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }
}
