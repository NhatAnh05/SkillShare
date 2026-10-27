package com.skillrental.config;

import com.skillrental.repository.UserRepository;
import com.skillrental.service.GoogleOidcUserService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
                                     GooglePasswordLoginSuccessHandler passwordSuccess)throws Exception {
        http.authorizeHttpRequests(a->a
                .requestMatchers("/","/login","/register","/forgot-password","/reset-password","/css/**","/js/**","/h2-console/**", "/customer/wallet/vnpay/return", "/verify-email").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/provider/**").hasRole("PROVIDER")
                .anyRequest().authenticated())
        .formLogin(f->f.loginPage("/login").loginProcessingUrl("/login").successHandler(passwordSuccess).failureUrl("/login?error").permitAll())
        .oauth2Login(o->o.loginPage("/login")
                .clientRegistrationRepository(clientRegistrations)
                .userInfoEndpoint(u->u.oidcUserService(googleOidcUserService))
                .defaultSuccessUrl("/workspace", true)
                .failureHandler(googleFailures))
        .rememberMe(r->r.key("skillrental-demo-remember-key-change-before-deploy").tokenValiditySeconds(604800))
        .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
        .headers(h->h.frameOptions(x->x.sameOrigin()))
        .csrf(c->c.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }
}
