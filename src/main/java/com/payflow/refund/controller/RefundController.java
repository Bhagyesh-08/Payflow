package com.payflow.refund.controller;

import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.mapper.RefundMapper;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final RefundRepository refundRepository;
    private final RefundMapper mapper;

    /**
     * Creates refund for payment.
     */
    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable Long paymentId,
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {

        Refund refund = refundService.createRefund(
            paymentId,
            merchantId,
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
    public ResponseEntity<RefundResponse> getRefund(@PathVariable String refundId) {
        Refund refund = refundService.getRefundById(refundId);
        return ResponseEntity.ok(mapper.toResponse(refund));
    }

    /**
     * Retrieves all refunds for a payment.
     */
    @GetMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<List<RefundResponse>> getPaymentRefunds(@PathVariable Long paymentId) {
        List<Refund> refunds = refundRepository.findByPaymentId(paymentId);
        List<RefundResponse> responses = refunds.stream()
            .map(mapper::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
