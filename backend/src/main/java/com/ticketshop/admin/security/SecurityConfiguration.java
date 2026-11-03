package com.ticketshop.admin.security;

import com.ticketshop.admin.authentication.api.AuthResponses;
import com.ticketshop.admin.authentication.application.AuthProblem;
import com.ticketshop.admin.common.api.ErrorCode;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Configuration
public class SecurityConfiguration {
    private HttpSecurity base(HttpSecurity http,AuthResponses responses) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(b -> b.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint((r,s,x) -> responses.write(r,s,new AuthProblem(ErrorCode.SESSION_INVALID)))
                .accessDeniedHandler((r,s,x) -> responses.write(r,s,new AuthProblem(ErrorCode.FORBIDDEN))));
    }
    @Bean @Order(1) SecurityFilterChain client(HttpSecurity http,AuthResponses responses) throws Exception {
        return base(http,responses).securityMatcher("/api/v1/client/**").authorizeHttpRequests(a -> a
            .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/client/login","/api/v1/client/first-password","/api/v1/client/validate","/api/v1/client/password","/api/v1/client/logout").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/client/account").permitAll().anyRequest().denyAll()).build();
    }
    @Bean @Order(2) SecurityFilterChain admin(HttpSecurity http,AuthResponses responses) throws Exception {
        return base(http,responses).securityMatcher("/api/v1/admin/**").authorizeHttpRequests(a -> a
            .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/admin/auth/csrf","/api/v1/admin/auth/me").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/admin/auth/login","/api/v1/admin/auth/first-password","/api/v1/admin/auth/activity","/api/v1/admin/auth/password","/api/v1/admin/auth/logout").permitAll().anyRequest().denyAll()).build();
    }
    @Bean @Order(3) SecurityFilterChain other(HttpSecurity http,AuthResponses responses) throws Exception {
        return base(http,responses).authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/liveness","/actuator/health/readiness").permitAll().anyRequest().denyAll()).build();
    }
}
