package com.payflow.merchant.controller;

import com.payflow.common.security.SecurityUtils;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantRegistrationResponse;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    public ResponseEntity<MerchantRegistrationResponse> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request) {
        MerchantRegistrationResponse response = merchantService.createMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchantResponse> getMerchantById(@PathVariable Long id) {
        SecurityUtils.validateMerchantAccess(id);
        MerchantResponse response = merchantService.getMerchantById(id);
        return ResponseEntity.ok(response);
    }
}
