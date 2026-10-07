package com.payflow.dispute.mapper;

import com.payflow.dispute.dto.DisputeResponse;
import com.payflow.dispute.entity.Dispute;
import org.springframework.stereotype.Component;

@Component
public class DisputeMapper {
    
    public DisputeResponse toResponse(Dispute dispute) {
        return DisputeResponse.builder()
            .disputeId(dispute.getDisputeId())
            .paymentId(dispute.getPayment().getPaymentId())
            .merchantId(dispute.getMerchant().getId())
            .amount(dispute.getAmount())
            .currency(dispute.getCurrency())
            .status(dispute.getStatus())
            .gateway(dispute.getGateway())
            .gatewayDisputeId(dispute.getGatewayDisputeId())
            .reason(dispute.getReason())
            .evidenceDueAt(dispute.getEvidenceDueAt())
            .resolvedAt(dispute.getResolvedAt())
            .resolutionReason(dispute.getResolutionReason())
            .createdAt(dispute.getCreatedAt())
            .updatedAt(dispute.getUpdatedAt())
            .build();
    }
}
