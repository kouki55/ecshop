package com.example.ecshop.cart;

import com.example.ecshop.auth.UserRepository;
import com.example.ecshop.pricing.PriceCalculator;
import com.example.ecshop.pricing.TaxRate;
import com.example.ecshop.product.Product;
import com.example.ecshop.product.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * カートの表示・追加・数量変更・削除。どのメソッドもログイン中の会員 ID（userId）を必ず受け取り、
 * その会員のカートだけを扱う。
 */
@Service
public class CartService {

    /** カートに入れられる商品の種類の上限。 */
    public static final int MAX_ITEMS = 30;

    public sealed interface Result {
        record Success() implements Result {
        }

        /** 在庫が足りない（在庫切れを含む）。 */
        record InsufficientStock() implements Result {
        }

        /** 足した結果、1 行の数量が上限を超える。 */
        record QuantityLimitExceeded() implements Result {
        }

        /** 商品の種類が上限に達している。 */
        record ItemLimitExceeded() implements Result {
        }

        /** 商品が販売終了（非公開）になっている。 */
        record Discontinued() implements Result {
        }
    }

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, UserRepository userRepository, Clock clock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartView view(long userId) {
        List<CartView.Line> lines = cartItemRepository.findByCartUserIdOrderByIdAsc(userId).stream()
                .map(CartService::toLine)
                .toList();
        // 購入できる明細だけで金額を計算する（販売終了・在庫切れの明細は合計に入れない）
        List<PriceCalculator.Line> purchasable = lines.stream()
                .filter(line -> line.status().purchasable())
                .map(line -> new PriceCalculator.Line(
                        line.unitPrice(), TaxRate.ofPercent(line.taxRatePercent()), line.quantity()))
                .toList();
        return new CartView(lines, PriceCalculator.calculate(purchasable));
    }

    @Transactional
    public Result add(long userId, long productId, int quantity) {
        // 会員の行をロックしてから処理する。同じ会員が同時に送っても、カートが 2 つ作られたり
        // 種類の上限を超えたりしないようにするため（2 つ目のリクエストは 1 つ目が終わるまで待つ）
        userRepository.findByIdForUpdate(userId).orElseThrow();
        // 非公開の商品と存在しない商品は、商品詳細と同じく 404
        Product product = productRepository.findByIdAndPublishedTrue(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Instant now = clock.instant();
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.create(userId, now)));
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        // 同じ商品がすでにあれば数量を足す
        int newQuantity = existing.map(item -> item.getQuantity() + quantity).orElse(quantity);
        if (newQuantity > CartItem.MAX_QUANTITY) {
            return new Result.QuantityLimitExceeded();
        }
        if (existing.isEmpty() && cartItemRepository.countByCartId(cart.getId()) >= MAX_ITEMS) {
            return new Result.ItemLimitExceeded();
        }
        // 在庫はここでは確保しない（減らすのは注文確定の時）。明らかに足りない数量だけ断る
        if (newQuantity > product.getStock()) {
            return new Result.InsufficientStock();
        }

        if (existing.isPresent()) {
            existing.get().changeQuantity(newQuantity, now);
        } else {
            cartItemRepository.save(CartItem.create(cart, product, newQuantity, now));
        }
        cart.touch(now);
        return new Result.Success();
    }

    @Transactional
    public Result changeQuantity(long userId, long itemId, int quantity) {
        CartItem item = findOwned(userId, itemId);
        Product product = item.getProduct();
        if (!product.isPublished()) {
            return new Result.Discontinued();
        }
        if (quantity > product.getStock()) {
            return new Result.InsufficientStock();
        }
        Instant now = clock.instant();
        item.changeQuantity(quantity, now);
        item.getCart().touch(now);
        return new Result.Success();
    }

    @Transactional
    public void remove(long userId, long itemId) {
        CartItem item = findOwned(userId, itemId);
        item.getCart().touch(clock.instant());
        cartItemRepository.delete(item);
    }

    /** 自分の明細であることを確かめる（入力エラーの画面を出す前にも使う）。 */
    @Transactional(readOnly = true)
    public void checkOwned(long userId, long itemId) {
        findOwned(userId, itemId);
    }

    /** 注文に使うカートの 1 行（商品 ID と数量だけ）。 */
    public record OrderLine(long productId, int quantity) {
    }

    /** 注文用に、カートの中身を商品 ID の小さい順に返す。 */
    @Transactional(readOnly = true)
    public List<OrderLine> orderLines(long userId) {
        return cartItemRepository.findOrderLines(userId);
    }

    /** 注文を確定したあとに、カートを空にする（カート自体は残す）。 */
    @Transactional
    public void clear(long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    /** 退会などで、その会員のカートを中身ごと削除する。 */
    @Transactional
    public void deleteCart(long userId) {
        cartItemRepository.deleteByUserId(userId);
        cartRepository.deleteByUserId(userId);
    }

    /** 他人の明細と存在しない明細は、区別できないよう同じ 404 にする。 */
    private CartItem findOwned(long userId, long itemId) {
        return cartItemRepository.findByIdAndCartUserId(itemId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static CartView.Line toLine(CartItem item) {
        Product product = item.getProduct();
        TaxRate taxRate = product.getTaxRate();
        return new CartView.Line(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                taxRate.includeTax(product.getPrice()),
                taxRate.percent(),
                item.getQuantity(),
                Math.multiplyExact(product.getPrice(), (long) item.getQuantity()),
                LineStatus.of(product.isPublished(), product.getStock(), item.getQuantity()));
    }
}
