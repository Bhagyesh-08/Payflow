package com.payflow.webhook.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.dispute.entity.Dispute;
import com.payflow.payment.entity.Payment;
import com.payflow.refund.entity.Refund;
import com.payflow.webhook.enums.WebhookStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WebhookEvent extends BaseEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String gatewayName;

    @Column(length = 200)
    private String gatewayPaymentId;

    @Column(length = 200)
    private String gatewayRefundId;

    @Column(length = 200)
    private String gatewayDisputeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id")
    private Dispute dispute;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(length = 500)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookStatus status;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
