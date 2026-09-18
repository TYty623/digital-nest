package com.digitalnest.petmemorial.shared.security;

import com.digitalnest.petmemorial.account.AccountRepository;
import com.digitalnest.petmemorial.account.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"data\":{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"请先登录后继续。\"}}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"data\":{\"code\":\"ACCESS_DENIED\",\"message\":\"没有操作权限或安全凭证已失效，请刷新后重试。\"}}");
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/health", "/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/m/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/memorials/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/*/content").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/memorials/public/*/tributes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/memorials/public/*/tributes/*/report").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/memorials/public/*/lights").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/memorials/public/*/unlock").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("MODERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CookieSerializer cookieSerializer(@Value("${app.security.cookies.secure:true}") boolean secureCookies) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("DIGITAL_NEST_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secureCookies);
        return serializer;
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accountRepository) {
        return email -> {
            UserAccount account = accountRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("找不到该账户"));
            String[] roles = accountRepository.findRolesByUserId(account.id()).stream()
                    .map(role -> "ROLE_" + role)
                    .toArray(String[]::new);
            return User.withUsername(account.email())
                    .password(account.passwordHash())
                    .authorities(roles)
                    .build();
        };
    }
}
