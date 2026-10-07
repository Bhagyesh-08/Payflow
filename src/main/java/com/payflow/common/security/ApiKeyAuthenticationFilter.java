package com.payflow.common.security;

import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * API Key Authentication Filter.
 * 
 * Authenticates requests using X-API-Key header.
 * Validates API key against merchant records.
 * Sets authenticated merchant ID in security context.
 * 
 * Portfolio Implementation Note:
 * - Uses simple API key stored in merchant table
 * - Production would use JWT, OAuth2, or dedicated IAM
 * - Demonstrates authentication/authorization boundary
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    
    private final MerchantRepository merchantRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isEmpty()) {
            Optional<Merchant> merchantOpt = merchantRepository.findByApiKey(apiKey);

            if (merchantOpt.isPresent()) {
                Merchant merchant = merchantOpt.get();
                
                // Create authenticated principal
                MerchantPrincipal principal = new MerchantPrincipal(
                    merchant.getId(),
                    merchant.getMerchantCode(),
                    merchant.getBusinessName()
                );

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.emptyList()
                    );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated merchant: {}", merchant.getMerchantCode());
            } else {
                log.warn("Invalid API key provided");
            }
        }

        filterChain.doFilter(request, response);
    }
}
