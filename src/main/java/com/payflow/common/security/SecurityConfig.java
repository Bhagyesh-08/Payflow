package com.payflow.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
 * 
 * Browser Access / CORS:
 * - Configured explicitly for frontend development origin (http://localhost:4200)
 * - Restricts allowed headers and methods without wildcard origins
 * - Preflight OPTIONS handled by Spring Security CORS filter
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isDevEnvironment = environment.acceptsProfiles(Profiles.of("dev", "local", "development"));

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // Stateless API, CSRF not applicable
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                // Public endpoints
                auth.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    
                    // OpenAPI/Swagger documentation (public for development)
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/swagger-ui.html").permitAll()
                    
                    // Webhook endpoints (use signature verification, not API key)
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll();

                // Development-only merchant provisioning (active only under dev/local profiles)
                if (isDevEnvironment) {
                    auth.requestMatchers("/api/v1/dev/**").permitAll();
                }

                // Protected business endpoints (require authentication)
                auth.requestMatchers("/api/v1/payments/**").authenticated()
                    .requestMatchers("/api/v1/refunds/**").authenticated()
                    .requestMatchers("/api/v1/disputes/**").authenticated()
                    .requestMatchers("/api/v1/orders/**").authenticated()
                    .requestMatchers("/api/v1/merchants/**").authenticated()
                    .requestMatchers("/api/v1/ledger/**").authenticated()
                    .requestMatchers("/api/v1/reconciliation/**").authenticated()
                    
                    // Prometheus metrics (consider restricting in production)
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .requestMatchers("/actuator/metrics").permitAll()
                    
                    // Block other actuator management endpoints
                    .requestMatchers("/actuator/**").denyAll()
                    
                    // Any other request requires authentication
                    .anyRequest().authenticated();
            })
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow strictly development frontend origin
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-API-Key",
            "X-Correlation-ID",
            "Idempotency-Key",
            "X-Merchant-Id"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
