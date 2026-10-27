package com.payflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private SecurityConfig securityConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ApiKeyAuthenticationFilter filter = mock(ApiKeyAuthenticationFilter.class);
        CustomAuthenticationEntryPoint entryPoint = mock(CustomAuthenticationEntryPoint.class);
        CustomAccessDeniedHandler accessHandler = mock(CustomAccessDeniedHandler.class);
        Environment env = mock(Environment.class);
        securityConfig = new SecurityConfig(filter, entryPoint, accessHandler, env);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void corsConfiguration_AllowsDevelopmentOrigin() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://localhost:4200")).isEqualTo("http://localhost:4200");
        assertThat(config.checkOrigin("http://malicious-site.com")).isNull();
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(config.getAllowedHeaders()).contains("X-API-Key", "X-Correlation-ID", "Idempotency-Key", "X-Merchant-Id");
        assertThat(config.getExposedHeaders()).contains("X-Correlation-ID");
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void authenticationEntryPoint_ReturnsStandardizedErrorResponse() throws Exception {
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");

        ErrorResponse error = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(error.getStatus()).isEqualTo(401);
        assertThat(error.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(error.getPath()).isEqualTo("/api/v1/payments");
        assertThat(error.getMessage()).isEqualTo("Full authentication is required to access this resource");
    }

    @Test
    void accessDeniedHandler_ReturnsStandardizedErrorResponse() throws Exception {
        CustomAccessDeniedHandler accessDeniedHandler = new CustomAccessDeniedHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");

        ErrorResponse error = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(error.getStatus()).isEqualTo(403);
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getPath()).isEqualTo("/api/v1/payments/1");
        assertThat(error.getMessage()).contains("Access denied");
    }
}
