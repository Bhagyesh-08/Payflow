package com.payflow.refund.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.SecurityUtils;
import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.mapper.RefundMapper;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Refund processing and management")
public class RefundController {

    private final RefundService refundService;
    private final RefundRepository refundRepository;
    private final RefundMapper mapper;

    /**
     * Creates refund for payment.
     * Derives merchant identity from authenticated API-key principal.
     * If legacy X-Merchant-Id header is supplied, validates that it matches the authenticated merchant.
     */
    @PostMapping("/payments/{paymentId}/refunds")
    @Operation(summary = "Create refund for a payment",
               description = "Creates a refund with idempotency protection. Full or partial refund supported.")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable Long paymentId,
            @Parameter(description = "Optional legacy Merchant ID header; must match authenticated merchant if provided") 
            @RequestHeader(value = "X-Merchant-Id", required = false) Long callerMerchantId,
            @Parameter(description = "Idempotency key for duplicate prevention", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {

        Long authenticatedMerchantId = SecurityUtils.getAuthenticatedMerchantId();
        if (callerMerchantId != null && !callerMerchantId.equals(authenticatedMerchantId)) {
            throw new BusinessException("FORBIDDEN", "X-Merchant-Id header does not match authenticated merchant");
        }

        Refund refund = refundService.createRefund(
            paymentId,
            authenticatedMerchantId,
            request.getAmount(),
            request.getCurrency(),
            request.getReason(),
            idempotencyKey
        );

        return ResponseEntity.ok(mapper.toResponse(refund));
    }

    /**
     * Retrieves refund by ID.
     */
    @GetMapping("/refunds/{refundId}")
    @Operation(summary = "Get refund by ID")
    public ResponseEntity<RefundResponse> getRefund(@PathVariable String refundId) {
        Refund refund = refundService.getRefundById(refundId);
        SecurityUtils.validateMerchantAccess(refund.getMerchant().getId());
        return ResponseEntity.ok(mapper.toResponse(refund));
    }

    /**
     * Retrieves all refunds for a payment.
     */
    @GetMapping("/payments/{paymentId}/refunds")
    @Operation(summary = "Get all refunds for a payment")
    public ResponseEntity<List<RefundResponse>> getPaymentRefunds(@PathVariable Long paymentId) {
        List<Refund> refunds = refundRepository.findByPaymentId(paymentId);
        if (!refunds.isEmpty()) {
            SecurityUtils.validateMerchantAccess(refunds.get(0).getMerchant().getId());
        }
        List<RefundResponse> responses = refunds.stream()
            .map(mapper::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
