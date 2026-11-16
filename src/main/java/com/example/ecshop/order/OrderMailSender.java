package com.example.ecshop.order;

import com.example.ecshop.auth.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 注文の確認メールを送る（ローカルでは Mailpit に届く）。
 * AFTER_COMMIT にすることで、支払いの記録が DB に確定したときだけ送る。
 * {@code @Async} で別スレッドで送るので、完了画面の表示がメール送信に左右されない。
 */
@Component
public class OrderMailSender {

    private static final Logger log = LoggerFactory.getLogger(OrderMailSender.class);

    private final MailSender mailSender;
    private final AuthProperties authProperties;

    public OrderMailSender(MailSender mailSender, AuthProperties authProperties) {
        this.mailSender = mailSender;
        this.authProperties = authProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderMailEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authProperties.mailFrom());
        message.setTo(event.email());
        message.setSubject("【ecshop】ご注文ありがとうございます（注文番号 " + event.order().id() + "）");
        message.setText(body(event));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 注文はもう確定しているので、送れなくても注文は取り消さない。
            // 例外メッセージには宛先が含まれることがあるので、ログには種類と注文番号だけを出す
            log.warn("注文の確認メールを送信できませんでした: orderId={}, {}", event.order().id(),
                    e.getClass().getName());
        }
    }

    static String body(OrderMailEvent event) {
        OrderView order = event.order();
        StringBuilder sb = new StringBuilder();
        sb.append(event.name()).append(" 様\n\n");
        sb.append("ecshop をご利用いただきありがとうございます。\n");
        sb.append("お支払いを確認しました。ご注文の内容は次のとおりです。\n\n");
        sb.append("注文番号：").append(order.id()).append('\n');
        sb.append("注文日時：").append(order.orderedAt()).append('\n');
        sb.append("お届け予定：").append(order.deliveryLabel()).append("\n\n");
        sb.append("■ ご注文の商品\n");
        for (OrderView.Line line : order.lines()) {
            sb.append("・").append(line.productName())
                    .append("　").append(yen(line.unitPrice())).append("（税抜・税率 ")
                    .append(line.taxRatePercent()).append("%）× ").append(line.quantity()).append('\n');
        }
        sb.append('\n');
        sb.append("商品小計（税抜）：").append(yen(order.subtotal())).append('\n');
        sb.append("消費税：").append(yen(order.taxAmount())).append('\n');
        sb.append("送料：").append(yen(order.shippingFee())).append('\n');
        sb.append("お支払い合計（税込）：").append(yen(order.totalAmount())).append("\n\n");
        sb.append("■ お届け先\n");
        sb.append(order.recipientName()).append(" 様\n");
        sb.append("〒").append(order.postalCode()).append(' ').append(order.address()).append("\n\n");
        sb.append("発送前であれば、ご注文をキャンセルできます。\n");
        sb.append("お心当たりのない場合は、このメールを破棄してください。\n");
        return sb.toString();
    }

    private static String yen(long amount) {
        return String.format("%,d円", amount);
    }
}
