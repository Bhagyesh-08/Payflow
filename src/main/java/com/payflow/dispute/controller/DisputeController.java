package com.payflow.dispute.controller;

import com.payflow.common.security.SecurityUtils;
import com.payflow.dispute.dto.CreateDisputeRequest;
import com.payflow.dispute.dto.DisputeResponse;
import com.payflow.dispute.dto.SubmitEvidenceRequest;
import com.payflow.dispute.entity.Dispute;
import com.payflow.dispute.mapper.DisputeMapper;
import com.payflow.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dispute REST API Controller.
 * 
 * All endpoints require X-API-Key authentication and enforce merchant-level authorization.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputeMapper disputeMapper;

    @PostMapping("/disputes")
    public ResponseEntity<DisputeResponse> createDispute(
            @Valid @RequestBody CreateDisputeRequest request) {

        Long merchantId = SecurityUtils.getCurrentMerchantId();
        
        log.info("Creating dispute: merchant={}, payment={}", merchantId, request.getPaymentId());

        Dispute dispute = disputeService.createDispute(
            request.getPaymentId(),
            merchantId,
            request.getAmount(),
            request.getCurrency(),
            request.getReason(),
            request.getGatewayDisputeId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(disputeMapper.toResponse(dispute));
    }

    @GetMapping("/disputes/{disputeId}")
    public ResponseEntity<DisputeResponse> getDispute(@PathVariable String disputeId) {
        Dispute dispute = disputeService.getDisputeByDisputeId(disputeId);
        SecurityUtils.validateMerchantAccess(dispute.getMerchant().getId());
        return ResponseEntity.ok(disputeMapper.toResponse(dispute));
    }

    @PostMapping("/disputes/{disputeId}/evidence")
    public ResponseEntity<DisputeResponse> submitEvidence(
            @PathVariable String disputeId,
            @Valid @RequestBody SubmitEvidenceRequest request) {

        log.info("Submitting evidence for dispute: {}", disputeId);

        Dispute dispute = disputeService.submitEvidence(disputeId, request.getEvidenceDescription());
        return ResponseEntity.ok(disputeMapper.toResponse(dispute));
    }

    @GetMapping("/payments/{paymentId}/disputes")
    public ResponseEntity<List<DisputeResponse>> getDisputesByPayment(@PathVariable Long paymentId) {
        List<Dispute> disputes = disputeService.getDisputesByPaymentId(paymentId);
        List<DisputeResponse> responses = disputes.stream()
            .map(disputeMapper::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/merchants/{merchantId}/disputes")
    public ResponseEntity<List<DisputeResponse>> getDisputesByMerchant(@PathVariable Long merchantId) {
        List<Dispute> disputes = disputeService.getDisputesByMerchantId(merchantId);
        List<DisputeResponse> responses = disputes.stream()
            .map(disputeMapper::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
