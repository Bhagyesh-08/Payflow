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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing and lifecycle management")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Get paginated payments for authenticated merchant",
               description = "Returns payments belonging to the authenticated merchant with pagination and sorting.")
    public ResponseEntity<Page<PaymentResponse>> getPayments(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1 to 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field (createdAt, amount, status, paymentId, id)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        if (page < 0) {
            throw new BusinessException("INVALID_PAGE", "Page index must not be negative");
        }
        if (size <= 0) {
            throw new BusinessException("INVALID_PAGE_SIZE", "Page size must be greater than zero");
        }
        if (size > 100) {
            throw new BusinessException("INVALID_PAGE_SIZE", "Page size must not exceed 100");
        }

        List<String> allowedSortFields = List.of("createdAt", "amount", "status", "paymentId", "id");
        if (!allowedSortFields.contains(sortBy)) {
            throw new BusinessException("INVALID_SORT_FIELD", "Sort field '" + sortBy + "' is not supported. Allowed: " + allowedSortFields);
        }

        Long authenticatedMerchantId = SecurityUtils.getAuthenticatedMerchantId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<PaymentResponse> response = paymentService.getPaymentsForMerchant(authenticatedMerchantId, pageable);

        return ResponseEntity.ok(response);
    }

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
