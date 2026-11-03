package com.skillrental.config;

import com.skillrental.repository.UserRepository;
import com.skillrental.service.GoogleOidcUserService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return username -> {
            var u=users.findByEmailIgnoreCase(username).orElseThrow(()->new UsernameNotFoundException("Không tìm thấy tài khoản"));
            if (u.getEmailVerified() == null || !u.getEmailVerified() || u.getAccountStatus() != com.skillrental.entity.AccountStatus.ACTIVE) {
                throw new org.springframework.security.authentication.DisabledException("Tài khoản chưa xác nhận email hoặc đang bị khóa.");
            }
            return org.springframework.security.core.userdetails.User.withUsername(u.getEmail()).password(u.getPassword()).roles(u.getRole().name()).build();
        }
        ;
    }
    @Bean SecurityFilterChain filter(HttpSecurity http, GoogleOidcUserService googleOidcUserService,
                                     ClientRegistrationRepository clientRegistrations,
                                     GoogleLoginFailureHandler googleFailures,
                                     GooglePasswordLoginSuccessHandler passwordSuccess, UserRepository users,
                                     @Value("${SKILLSHARE_REMEMBER_ME_KEY:}") String rememberKey)throws Exception {
        http.addFilterBefore(new PromotedAdminSessionFilter(users), org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.authorizeHttpRequests(a->a
                .requestMatchers("/","/login","/register","/register/send-otp","/forgot-password","/reset-password","/css/**","/js/**", "/customer/wallet/vnpay/return", "/verify-email", "/verify-otp", "/registration-otp/send", "/resend-registration-otp", "/policies/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/provider/**").hasRole("PROVIDER")
                .anyRequest().authenticated())
        .formLogin(f->f.loginPage("/login").loginProcessingUrl("/login").successHandler(passwordSuccess).failureUrl("/login?error").permitAll())
        .oauth2Login(o->o.loginPage("/login")
                .clientRegistrationRepository(clientRegistrations)
                .userInfoEndpoint(u->u.oidcUserService(googleOidcUserService))
                .defaultSuccessUrl("/workspace", true)
                .failureHandler(googleFailures))
        .rememberMe(r->r.key(rememberKey == null || rememberKey.isBlank()
                ? UUID.randomUUID().toString() : rememberKey).tokenValiditySeconds(604800))
        .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
        .headers(h->h.frameOptions(x->x.sameOrigin()))
        .csrf(c->c.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }
}
