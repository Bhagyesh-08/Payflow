package com.payflow.payment.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.SecurityUtils;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {
        
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }
        
        // Extract merchant ID from authenticated context
        Long merchantId = SecurityUtils.getAuthenticatedMerchantId();
        
        PaymentResponse response = paymentService.createPayment(request, merchantId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/initiate")
    public ResponseEntity<PaymentResponse> initiatePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.initiatePayment(id);
        // Authorization checked in service layer
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/authorize")
    public ResponseEntity<PaymentResponse> authorizePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.authorizePayment(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<PaymentResponse> capturePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.capturePayment(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payment-id/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentByPaymentId(@PathVariable String paymentId) {
        PaymentResponse response = paymentService.getPaymentByPaymentId(paymentId);
        return ResponseEntity.ok(response);
    }
}
