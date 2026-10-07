package com.payflow.refund.mapper;

import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.entity.Refund;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundResponse toResponse(Refund refund) {
        return RefundResponse.builder()
            .id(refund.getId())
            .refundId(refund.getRefundId())
            .paymentId(refund.getPayment().getPaymentId())
            .merchantId(refund.getMerchant().getId())
            .amount(refund.getAmount())
            .currency(refund.getCurrency())
            .status(refund.getStatus().name())
            .gateway(refund.getGateway())
            .gatewayRefundId(refund.getGatewayRefundId())
            .reason(refund.getReason())
            .failureReason(refund.getFailureReason())
            .createdAt(refund.getCreatedAt())
            .updatedAt(refund.getUpdatedAt())
            .build();
    }
}
