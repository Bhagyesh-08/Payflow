package com.payflow.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration.
 * 
 * Authentication Model:
 * - API Key based authentication for merchants
 * - Webhook signature verification (separate from merchant auth)
 * - Stateless (no sessions)
 * 
 * Authorization Model:
 * - Merchant can only access their own resources
 * - Merchant ID extracted from authenticated API key
 * - Cross-merchant access blocked
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Stateless API, CSRF not applicable
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // Webhook endpoints (use signature verification, not API key)
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                
                // Protected business endpoints (require authentication)
                .requestMatchers("/api/v1/payments/**").authenticated()
                .requestMatchers("/api/v1/refunds/**").authenticated()
                .requestMatchers("/api/v1/orders/**").authenticated()
                .requestMatchers("/api/v1/merchants/**").authenticated()
                .requestMatchers("/api/v1/ledger/**").authenticated()
                .requestMatchers("/api/v1/reconciliation/**").authenticated()
                
                // Block actuator management endpoints
                .requestMatchers("/actuator/**").denyAll()
                
                // Any other request requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
