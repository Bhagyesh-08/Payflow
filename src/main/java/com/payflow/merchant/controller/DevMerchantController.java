package com.payflow.merchant.controller;

import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantRegistrationResponse;
import com.payflow.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Development-Only Merchant Provisioning Controller.
 * 
 * Active ONLY under "dev" or "local" Spring profiles.
 * Never available or loaded in production.
 * Generates an initial merchant with API key for local developer testing.
 */
@Profile({"dev", "local"})
@RestController
@RequestMapping("/api/v1/dev/merchants")
@RequiredArgsConstructor
@Slf4j
public class DevMerchantController {

    private final MerchantService merchantService;

    @PostMapping("/provision")
    public ResponseEntity<MerchantRegistrationResponse> provisionDevMerchant(
            @RequestBody(required = false) CreateMerchantRequest request) {

        long suffix = System.currentTimeMillis() % 100000;
        if (request == null || request.getBusinessName() == null || request.getBusinessName().isBlank()) {
            request = CreateMerchantRequest.builder()
                    .businessName("Local Dev Store " + suffix)
                    .email("dev_" + suffix + "@payflow.local")
                    .phone("9999999999")
                    .address("123 Localhost Way")
                    .build();
        }

        MerchantRegistrationResponse response = merchantService.createMerchant(request);
        log.info("[DEV PROVISIONING] Initialized development merchant: ID={}, code={}", 
                response.getId(), response.getMerchantCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
