package com.example.ecshop.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecshop.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** dev 以外のプロファイル（テスト・本番）では、練習用データを入れる Bean 自体が作られない。 */
@IntegrationTest
class DevDataLoaderProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void dev以外では練習用データを入れない() {
        assertThat(context.getBeansOfType(DevDataLoader.class)).isEmpty();
    }
}
