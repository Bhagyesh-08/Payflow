package com.payflow.refund.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.refund.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "refunds", uniqueConstraints = {
    @UniqueConstraint(name = "uk_refund_idempotency", columnNames = {"merchant_id", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Refund extends BaseEntity {

    @Column(name = "refund_id", nullable = false, unique = true, length = 100)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(length = 50)
    private String gateway;

    @Column(name = "gateway_refund_id", length = 200)
    private String gatewayRefundId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(length = 1000)
    private String reason;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
}
