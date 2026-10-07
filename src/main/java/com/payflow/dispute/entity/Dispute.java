package com.payflow.dispute.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.dispute.enums.DisputeStatus;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispute Entity.
 * 
 * Represents a payment dispute/chargeback initiated by customer or card network.
 * Separate from merchant-initiated refunds.
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Dispute extends BaseEntity {

    @Column(name = "dispute_id", nullable = false, unique = true, length = 100)
    private String disputeId;

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
    @Column(nullable = false, length = 30)
    private DisputeStatus status;

    @Column(length = 50)
    private String gateway;

    @Column(name = "gateway_dispute_id", length = 200, unique = true)
    private String gatewayDisputeId;

    @Column(length = 1000)
    private String reason;

    @Column(name = "evidence_due_at")
    private LocalDateTime evidenceDueAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_reason", length = 1000)
    private String resolutionReason;
}
