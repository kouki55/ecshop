package com.example.ecshop.order;

import com.example.ecshop.auth.Address;
import com.example.ecshop.auth.AddressService;
import com.example.ecshop.auth.User;
import com.example.ecshop.auth.UserRepository;
import com.example.ecshop.cart.CartService;
import com.example.ecshop.cart.CartView;
import com.example.ecshop.payment.Payment;
import com.example.ecshop.payment.PaymentGateway;
import com.example.ecshop.payment.PaymentRepository;
import com.example.ecshop.payment.PaymentStatus;
import com.example.ecshop.pricing.PriceCalculator;
import com.example.ecshop.product.Product;
import com.example.ecshop.product.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 注文の確認・確定・支払い・履歴・キャンセル（会員による・自動）。会員が操作するメソッドは、ログイン中の会員 ID（userId）を必ず受け取り、
 * その会員の注文・住所・カートだけを扱う。
 *
 * <p>注文の確定は 2 つのトランザクションに分ける。
 * <ol>
 *   <li>在庫を減らして注文を作る（商品の行を悲観ロック）。ここでコミットして、商品のロックをすぐ外す</li>
 *   <li>注文の行をロックして決済し、結果を記録する</li>
 * </ol>
 * 決済サービスの応答には数秒かかることがあるので、その間ほかの会員の注文（同じ商品の在庫）を待たせないため。
 * 同じクラスの中で {@code @Transactional} のメソッドを呼んでもトランザクションは分かれない（Spring の仕組み上、
 * 外から呼ばれたときだけ効く）ので、ここでは {@link TransactionTemplate} で範囲を明示している。
 */
@Service
public class OrderService {

    /** 確認画面の表示の結果。 */
    public sealed interface ConfirmResult {
        record Ready(ConfirmView view) implements ConfirmResult {
        }

        /** カートが空か、購入できない明細（販売終了・在庫不足）がある。 */
        record CartNotReady() implements ConfirmResult {
        }
    }

    /** 確認画面に渡す情報。idempotencyKey は表示のたびに新しく作る。 */
    public record ConfirmView(CartView cart, Address address, String deliveryLabel,
                              List<PaymentGateway.PaymentMethod> paymentMethods, UUID idempotencyKey) {
        public ConfirmView {
            paymentMethods = List.copyOf(paymentMethods);
        }
    }

    /** 注文の確定の結果。 */
    public sealed interface PlaceResult {
        record Paid(long orderId) implements PlaceResult {
        }

        /** 注文は受け付けたが、支払いに失敗した（再試行できる）。 */
        record PaymentFailed(long orderId) implements PlaceResult {
        }

        /** 同じ冪等キーの注文がすでにある（二重送信）。新しい注文は作っていない。 */
        record AlreadyPlaced(long orderId, OrderStatus status) implements PlaceResult {
        }

        /** カートが空か、購入できない明細がある。 */
        record CartNotReady() implements PlaceResult {
        }

        /** 確認画面を表示したあとに、合計金額が変わった。 */
        record PriceChanged() implements PlaceResult {
        }
    }

    /** 支払いの結果。 */
    public sealed interface PayResult {
        record Paid() implements PayResult {
        }

        record Failed() implements PayResult {
        }

        /** 支払える状態ではない（支払済・キャンセル済など）。status は今の状態。 */
        record NotPayable(OrderStatus status) implements PayResult {
        }
    }

    /** 会員によるキャンセルの結果。 */
    public sealed interface CancelResult {
        /** キャンセルした。refunded は、支払い済みだったので返金したかどうか。 */
        record Cancelled(boolean refunded) implements CancelResult {
        }

        /** 返金に失敗したので、キャンセルしていない（注文は支払済のまま）。 */
        record RefundFailed() implements CancelResult {
        }

        /** キャンセルできない状態（発送済・完了・キャンセル済）。status は今の状態。 */
        record NotCancellable(OrderStatus status) implements CancelResult {
        }
    }

    /** 注文履歴の 1 ページに出す件数。 */
    public static final int HISTORY_PAGE_SIZE = 20;

    /** 注文履歴のページ番号の上限（極端に大きい値で DB に無駄な検索をさせないため）。 */
    public static final int HISTORY_MAX_PAGE = 10_000;

    // 新しい注文が上。注文日時が同じときも並びが毎回変わらないよう、ID でも並べる
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id"));

    /** 注文を作る処理の結果（このクラスの中だけで使う）。 */
    private sealed interface Creation {
        record Created(long orderId) implements Creation {
        }

        record Rejected(PlaceResult result) implements Creation {
        }
    }

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AddressService addressService;
    private final CartService cartService;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher events;
    private final OrderProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        PaymentRepository paymentRepository, UserRepository userRepository,
                        AddressService addressService, CartService cartService, PaymentGateway paymentGateway,
                        ApplicationEventPublisher events, OrderProperties properties, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.addressService = addressService;
        this.cartService = cartService;
        this.paymentGateway = paymentGateway;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- 配送先・確認

    /** カートに注文できる商品だけが入っているか（配送先の選択画面に進めるか）。 */
    @Transactional(readOnly = true)
    public boolean isCartReady(long userId) {
        CartView cart = cartService.view(userId);
        return !cart.isEmpty() && !cart.hasUnavailableLines();
    }

    @Transactional(readOnly = true)
    public ConfirmResult confirm(long userId, long addressId) {
        // 他人の住所 ID なら 404（AddressService が会員 ID を条件に取得する）
        Address address = addressService.get(userId, addressId);
        CartView cart = cartService.view(userId);
        if (cart.isEmpty() || cart.hasUnavailableLines()) {
            return new ConfirmResult.CartNotReady();
        }
        // UUID.randomUUID() は内部で SecureRandom を使うので、他人のキーを推測されない
        return new ConfirmResult.Ready(new ConfirmView(cart, address,
                DeliveryEstimate.from(clock.instant()).label(), paymentGateway.availableMethods(),
                UUID.randomUUID()));
    }

    // ---------------------------------------------------------------- 確定

    /** 注文を確定し、続けて支払う。 */
    public PlaceResult place(long userId, PlaceOrderForm form) {
        requireSupported(form.getPaymentToken());
        Creation creation = Objects.requireNonNull(tx.execute(status -> createOrder(userId, form)));
        return switch (creation) {
            case Creation.Rejected rejected -> rejected.result();
            case Creation.Created created -> switch (pay(userId, created.orderId(), form.getPaymentToken())) {
                case PayResult.Paid paid -> new PlaceResult.Paid(created.orderId());
                case PayResult.Failed failed -> new PlaceResult.PaymentFailed(created.orderId());
                // 確定の直後なので通常は起きないが、別のタブから同時に支払われた場合など
                case PayResult.NotPayable notPayable ->
                        new PlaceResult.AlreadyPlaced(created.orderId(), notPayable.status());
            };
        };
    }

    private Creation createOrder(long userId, PlaceOrderForm form) {
        // 会員の行をロックして、同じ会員の注文の確定を 1 件ずつ処理する。
        // 同じ冪等キーで同時に 2 回送られても、2 回目は 1 回目のコミットを待ってから下の検索で見つける
        userRepository.findByIdForUpdate(userId).orElseThrow();
        Optional<Order> existing = orderRepository.findByIdempotencyKey(form.getIdempotencyKey());
        if (existing.isPresent()) {
            Order order = existing.get();
            if (!order.getUserId().equals(userId)) {
                // 他人のキー。ランダムな UUID が偶然重なることはないので、書き換えられたリクエスト
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            return new Creation.Rejected(new PlaceResult.AlreadyPlaced(order.getId(), order.getStatus()));
        }

        Address address = addressService.get(userId, form.getAddressId());
        List<CartService.OrderLine> lines = cartService.orderLines(userId);
        if (lines.isEmpty()) {
            return new Creation.Rejected(new PlaceResult.CartNotReady());
        }

        // 在庫を確かめて減らすまでの間に、ほかの注文に在庫を取られないよう商品の行をロックする
        Map<Long, Product> products = lockProducts(lines.stream().map(CartService.OrderLine::productId).toList());
        List<PriceCalculator.Line> priceLines = new ArrayList<>();
        for (CartService.OrderLine line : lines) {
            Product product = products.get(line.productId());
            if (!product.isPublished() || product.getStock() < line.quantity()) {
                return new Creation.Rejected(new PlaceResult.CartNotReady());
            }
            priceLines.add(new PriceCalculator.Line(product.getPrice(), product.getTaxRate(), line.quantity()));
        }
        PriceCalculator.Result price = PriceCalculator.calculate(priceLines);
        if (price.total() != form.getExpectedTotal()) {
            return new Creation.Rejected(new PlaceResult.PriceChanged());
        }

        Instant now = clock.instant();
        Order order = Order.create(userId, form.getIdempotencyKey(), address, price, now);
        for (CartService.OrderLine line : lines) {
            Product product = products.get(line.productId());
            order.addItem(product, line.quantity());
            product.decreaseStock(line.quantity(), now);
        }
        orderRepository.save(order);
        cartService.clear(userId);
        return new Creation.Created(order.getId());
    }

    // ---------------------------------------------------------------- 支払い

    /** 受付の状態の注文を支払う（確定の直後と、失敗したあとの再試行で使う）。 */
    public PayResult pay(long userId, long orderId, String paymentToken) {
        requireSupported(paymentToken);
        return Objects.requireNonNull(tx.execute(status -> payInTransaction(userId, orderId, paymentToken)));
    }

    private PayResult payInTransaction(long userId, long orderId, String paymentToken) {
        // 注文の行をロックしたまま決済する。同じ注文の支払いが同時に 2 回送られても、2 回目は 1 回目の
        // 結果（支払済）を見てから判断するので、二重に請求しない。自動キャンセルとも同時には動かない
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.RECEIVED) {
            return new PayResult.NotPayable(order.getStatus());
        }
        Instant now = clock.instant();
        if (isExpired(order, now)) {
            // 支払い期限を過ぎていれば、自動キャンセルを待たずにここでキャンセルする
            cancelAndRestock(order, now);
            return new PayResult.NotPayable(OrderStatus.CANCELLED);
        }

        // 再試行のたびに別の依頼として扱ってもらうため、決済サービスに渡すキーには回数を付ける
        long attempt = paymentRepository.countByOrderId(orderId) + 1;
        PaymentGateway.ChargeResult result = paymentGateway.charge(new PaymentGateway.ChargeRequest(
                orderId, order.getIdempotencyKey() + "-" + attempt, order.getTotalAmount(), paymentToken));
        paymentRepository.save(Payment.record(orderId, result, order.getTotalAmount(), now));
        if (!result.succeeded()) {
            return new PayResult.Failed();
        }

        order.changeStatus(OrderStatus.PAID, now);
        User user = userRepository.findById(userId).orElseThrow();
        // メールはコミット後に送る（OrderMailSender）。ここで発行しておけば、記録に失敗したときは送られない
        events.publishEvent(new OrderMailEvent(user.getEmail(), user.getName(),
                OrderView.of(order, properties.paymentTimeout())));
        return new PayResult.Paid();
    }

    private void requireSupported(String paymentToken) {
        // 選択肢にない値は、画面を使わずに書き換えて送られたリクエスト
        if (!paymentGateway.supports(paymentToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------------------------------------------------------- 表示

    public List<PaymentGateway.PaymentMethod> paymentMethods() {
        return paymentGateway.availableMethods();
    }

    /** 自分の注文を 1 件取得する。他人の注文と存在しない ID は、区別できないよう同じ 404 にする。 */
    @Transactional(readOnly = true)
    public OrderView get(long userId, long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return OrderView.of(order, properties.paymentTimeout());
    }

    /** 自分の注文を新しい順に 1 ページ分返す（page は 0 から）。他人の注文は含まない。 */
    @Transactional(readOnly = true)
    public Page<OrderSummary> list(long userId, int page) {
        Page<Order> orders = orderRepository.findByUserId(userId, PageRequest.of(page, HISTORY_PAGE_SIZE, NEWEST_FIRST));
        List<Long> orderIds = orders.map(Order::getId).getContent();
        // 注文 ID ごとに商品名をまとめる（明細の ID 順に並んでいる）
        Map<Long, List<String>> namesByOrder = orderIds.isEmpty() ? Map.of()
                : orderRepository.findItemNamesByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemName::orderId,
                        Collectors.mapping(OrderItemName::productName, Collectors.toList())));
        return orders.map(order -> OrderSummary.of(order, namesByOrder.getOrDefault(order.getId(), List.of())));
    }

    // ---------------------------------------------------------------- 会員によるキャンセル

    /**
     * 発送前（受付・支払済）の自分の注文をキャンセルし、在庫を戻す。支払済なら先に返金する。
     * 返金に失敗したときは何も変えない（お金を返さずにキャンセル済みにしてしまわないため）。
     */
    public CancelResult cancel(long userId, long orderId) {
        return Objects.requireNonNull(tx.execute(status -> cancelInTransaction(userId, orderId)));
    }

    private CancelResult cancelInTransaction(long userId, long orderId) {
        // 注文の行をロックする。支払い・自動キャンセル・キャンセルの連打と同時に動かないようにするため。
        // 他人の注文 ID なら、存在しない ID と同じ 404
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getStatus().isCancellable()) {
            return new CancelResult.NotCancellable(order.getStatus());
        }
        Instant now = clock.instant();
        boolean refunded = false;
        if (order.getStatus() == OrderStatus.PAID) {
            Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
                    // 支払済なら成功した支払いが必ずある（支払いの記録と状態の変更は同じトランザクション）
                    .orElseThrow(() -> new IllegalStateException("成功した支払いが見つかりません"));
            PaymentGateway.RefundResult result = paymentGateway.refund(new PaymentGateway.RefundRequest(
                    orderId, payment.getTransactionId(), order.getIdempotencyKey() + "-refund",
                    payment.getAmount()));
            if (!result.succeeded()) {
                return new CancelResult.RefundFailed();
            }
            payment.markRefunded(result.transactionId(), now);
            refunded = true;
        }
        cancelAndRestock(order, now);
        return new CancelResult.Cancelled(refunded);
    }

    // ---------------------------------------------------------------- 自動キャンセル

    /**
     * 支払い期限を過ぎた「受付」の注文をキャンセルし、在庫を戻す。キャンセルした件数を返す。
     * 1 件ずつ別のトランザクションにして、1 件の失敗でほかの注文の処理まで取り消されないようにする。
     */
    public int cancelExpiredOrders() {
        Instant now = clock.instant();
        List<Long> ids = orderRepository.findIdsByStatusOrderedBefore(OrderStatus.RECEIVED,
                now.minus(properties.paymentTimeout()));
        int cancelled = 0;
        for (Long id : ids) {
            if (Boolean.TRUE.equals(tx.execute(status -> cancelIfExpired(id, now)))) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelIfExpired(long orderId, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        // 検索してからロックするまでの間に支払われているかもしれないので、ロックしてから確かめ直す
        if (order.getStatus() != OrderStatus.RECEIVED || !isExpired(order, now)) {
            return false;
        }
        cancelAndRestock(order, now);
        return true;
    }

    /**
     * 支払い期限（受付から payment-timeout 後）を過ぎたか。期限の時刻ちょうどまでは支払える。
     * OrderRepository.findIdsByStatusOrderedBefore（ordered_at &lt; 今 - 期限）と同じ境目にしている。
     */
    private boolean isExpired(Order order, Instant now) {
        return now.isAfter(order.getOrderedAt().plus(properties.paymentTimeout()));
    }

    /** キャンセルして在庫を戻す。呼び出す前に注文の行をロックしておくこと。 */
    private void cancelAndRestock(Order order, Instant now) {
        List<OrderItem> items = order.getItems();
        Map<Long, Product> products = lockProducts(items.stream().map(OrderItem::getProductId).toList());
        for (OrderItem item : items) {
            products.get(item.getProductId()).increaseStock(item.getQuantity(), now);
        }
        order.changeStatus(OrderStatus.CANCELLED, now);
    }

    /** 商品の行を ID の小さい順に悲観ロックで取得する（順番をそろえてデッドロックを防ぐ）。 */
    private Map<Long, Product> lockProducts(List<Long> productIds) {
        Map<Long, Product> products = productRepository.findAllByIdForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != Set.copyOf(productIds).size()) {
            // 外部キーがあるので通常は起きない
            throw new IllegalStateException("商品が見つかりません");
        }
        return products;
    }
}
