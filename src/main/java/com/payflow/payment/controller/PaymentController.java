package com.payflow.payment.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.SecurityUtils;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing and lifecycle management")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a new payment", 
               description = "Creates a payment with idempotency protection. Idempotency-Key header required.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error or business rule violation"),
        @ApiResponse(responseCode = "409", description = "Duplicate payment (idempotency key already used)")
    })
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @Parameter(description = "Idempotency key for duplicate prevention", required = true)
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
    @Operation(summary = "Initiate payment with gateway")
    public ResponseEntity<PaymentResponse> initiatePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.initiatePayment(id);
        // Authorization checked in service layer
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/authorize")
    @Operation(summary = "Authorize payment")
    public ResponseEntity<PaymentResponse> authorizePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.authorizePayment(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/capture")
    @Operation(summary = "Capture authorized payment")
    public ResponseEntity<PaymentResponse> capturePayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.capturePayment(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by database ID")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payment-id/{paymentId}")
    @Operation(summary = "Get payment by payment ID (business identifier)")
    public ResponseEntity<PaymentResponse> getPaymentByPaymentId(@PathVariable String paymentId) {
        PaymentResponse response = paymentService.getPaymentByPaymentId(paymentId);
        return ResponseEntity.ok(response);
    }
}
