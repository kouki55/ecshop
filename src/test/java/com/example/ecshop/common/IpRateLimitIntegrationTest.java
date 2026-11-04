package com.example.ecshop.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecshop.TestBeansConfiguration;
import com.example.ecshop.TestUsers;
import com.example.ecshop.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 回数制限のフィルタが SecurityFilterChain に組み込まれていることの確認。
 * 上限を小さくするため、ほかの結合テストとは別のコンテキストで動かす。
 */
@SpringBootTest(properties = "app.rate-limit.login.max-requests=3")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestBeansConfiguration.class})
class IpRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 同じIPからのログインが上限を超えると429になる() throws Exception {
        for (int i = 0; i < 3; i++) {
            login().andExpect(redirectedUrl("/login?error"));
        }
        // MockMvc はエラー画面への転送までは行わないので、ステータスコードだけを確認する
        login().andExpect(status().isTooManyRequests());
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/login")
                .param("email", TestUsers.randomEmail())
                .param("password", TestUsers.PASSWORD)
                .with(csrf()));
    }
}
