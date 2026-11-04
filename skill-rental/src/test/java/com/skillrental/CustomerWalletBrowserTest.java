package com.skillrental;

import com.microsoft.playwright.*;
import com.skillrental.entity.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.UserWalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "skillshare.wallet.demo-topup-enabled=true"
})
class CustomerWalletBrowserTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void customerCanSignInAndUseClearlyLabeledLocalDemoWalletTopUp() {
        Path chrome = findBrowser();
        assumeTrue(chrome != null, "Install Chrome/Edge or set CHROME_BIN to run the real-browser smoke test.");

        String password = "Browser-test-Password-42";
        User customer = users.saveAndFlush(User.builder()
                .fullName("Browser wallet customer")
                .email("browser-" + java.util.UUID.randomUUID() + "@browser.test")
                .password(passwordEncoder.encode(password))
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setExecutablePath(chrome))) {
            Page page = browser.newPage();
            page.navigate("http://127.0.0.1:" + port + "/login");
            page.locator("input[name='username']").fill(customer.getEmail());
            page.locator("input[name='password']").fill(password);
            page.locator("button[type='submit']").click();
            assertFalse(page.url().endsWith("/login"), "A valid Customer should leave the login page.");

            page.navigate("http://127.0.0.1:" + port + "/customer/wallet");
            assertTrue(page.getByText("Nạp tiền vào ví qua VNPay Sandbox").isVisible());
            assertTrue(page.getByText("Chưa cấu hình VNPay Sandbox").isVisible());
            page.locator("details:has(form[action='/customer/wallet/topup-demo']) > summary").click();
            page.locator("#demoAmount").fill("10000");
            page.locator("form[action='/customer/wallet/topup-demo'] button[type='submit']").click();
            assertTrue(page.locator("body").innerText().contains("DEMO_TOPUP"),
                    "The wallet page should show the posted demo ledger entry. Body: " + page.locator("body").innerText());
        }

        assertEquals(0, wallets.findByUser(customer).orElseThrow().getBalance()
                .compareTo(new java.math.BigDecimal("10000.00")));
    }

    private Path findBrowser() {
        String configured = System.getenv("CHROME_BIN");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path)) return path;
        }
        for (String candidate : new String[] {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser"
        }) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }
}
