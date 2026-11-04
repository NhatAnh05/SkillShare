package com.skillrental;

import com.skillrental.config.GoogleOAuthConfiguration.GoogleLoginSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Missing Google keys must not crash the password-login application. */
@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET="})
@AutoConfigureMockMvc
class GoogleLoginConfigurationOffTest {
    @Autowired private MockMvc mvc;
    @Autowired private GoogleLoginSettings settings;

    @Test
    void googleButtonIsDisabledButPasswordLoginRemainsAvailable() throws Exception {
        assertFalse(settings.enabled());
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google Login chưa được cấu hình")))
                .andExpect(content().string(not(containsString("href=\"/oauth2/authorization/google\""))))
                .andExpect(content().string(containsString("name=\"username\"")));
    }
}
