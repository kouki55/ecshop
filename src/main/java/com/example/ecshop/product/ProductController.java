package com.example.ecshop.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品一覧と商品詳細。未ログインでも見られる（購入は会員だけ）。
 *
 * <p>引数の @Min / @Max は Spring MVC が自動で検証し、範囲外なら 400 になる。
 * クラスに @Validated を付けると別の仕組み（AOP）で検証されて 500 になるので付けない。
 */
@Controller
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // URL の size や sort は引数にないので、付けられても無視される
    @GetMapping("/products")
    public String list(@RequestParam(name = "category", required = false) Long categoryId,
                       @RequestParam(name = "page", defaultValue = "0")
                       @Min(0) @Max(ProductService.MAX_PAGE) int page,
                       Model model) {
        Page<ProductView> products = productService.list(categoryId, page);
        model.addAttribute("products", products);
        model.addAttribute("categories", productService.categories());
        model.addAttribute("categoryId", categoryId);
        return "product/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("product", productService.get(id));
        return "product/detail";
    }
}
