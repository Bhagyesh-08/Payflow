package com.payflow.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.dispute.entity.Dispute;
import com.payflow.dispute.enums.DisputeStatus;
import com.payflow.dispute.repository.DisputeRepository;
import com.payflow.event.domain.DisputeEventType;
import com.payflow.event.domain.PaymentEventType;
import com.payflow.event.domain.RefundEventType;
import com.payflow.event.service.DisputeEventService;
import com.payflow.event.service.PaymentEventService;
import com.payflow.event.service.RefundEventService;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.statemachine.PaymentStateMachine;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.webhook.entity.WebhookEvent;
import com.payflow.webhook.enums.WebhookStatus;
import com.payflow.webhook.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Webhook Processing Service
 * 
 * Handles idempotent webhook processing with:
 * - Event deduplication using database unique constraint
 * - Payment state machine validation
 * - Transaction boundaries for consistency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final BusinessMetrics metrics;    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentStateMachine stateMachine;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final PaymentEventService paymentEventService;
    private final RefundEventService refundEventService;
    private final DisputeEventService disputeEventService;

    /**
     * Processes Razorpay webhook event.
     * 
     * Flow:
     * 1. Verify signature
     * 2. Parse event
     * 3. Check for duplicate (database unique constraint)
     * 4. Process business logic
     * 5. Update payment state
     * 
     * @param rawPayload the exact raw request body
     * @param signature the X-Razorpay-Signature header
     */
    @Transactional
    public void processWebhook(String rawPayload, String signature) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing Razorpay webhook");

        // Step 1: Verify signature on raw payload
        try {
            signatureVerifier.verifySignature(rawPayload, signature);
        } catch (BusinessException e) {
            log.warn("Webhook signature verification failed: reason={}", e.getMessage());
            metrics.incrementWebhooksRejected("signature_verification");
            throw e;
        }

        // Step 2: Parse event
        JsonNode eventData;
        String eventId;
        String eventType;
        String gatewayPaymentId = null;
        String gatewayRefundId = null;
        String gatewayDisputeId = null;

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            eventId = root.path("id").asText();
            eventType = root.path("event").asText();
            
            MDC.put("eventId", eventId);
            MDC.put("eventType", eventType);

            if (eventId.isEmpty() || eventType.isEmpty()) {
                throw new BusinessException("WEBHOOK_INVALID_PAYLOAD", 
                    "Missing required fields in webhook payload");
            }

            // Parse based on event type
            if (eventType.startsWith("payment.dispute.")) {
                // Dispute event: payload.dispute.entity
                JsonNode payload = root.path("payload").path("dispute").path("entity");
                gatewayDisputeId = payload.path("id").asText();
                gatewayPaymentId = payload.path("payment_id").asText();
                eventData = payload;
                log.info("Webhook event: type={}, id={}, gateway_dispute_id={}, gateway_payment_id={}", 
                    eventType, eventId, gatewayDisputeId, gatewayPaymentId);
            } else if (eventType.startsWith("payment.refund.")) {
                // Refund event: payload.refund.entity
                JsonNode payload = root.path("payload").path("refund").path("entity");
                gatewayRefundId = payload.path("id").asText();
                gatewayPaymentId = payload.path("payment_id").asText();
                eventData = payload;
                log.info("Webhook event: type={}, id={}, gateway_refund_id={}, gateway_payment_id={}", 
                    eventType, eventId, gatewayRefundId, gatewayPaymentId);
            } else {
                // Payment event: payload.payment.entity
                JsonNode payload = root.path("payload").path("payment").path("entity");
                gatewayPaymentId = payload.path("id").asText();
                eventData = payload;
                log.info("Webhook event: type={}, id={}, gateway_payment_id={}", 
                    eventType, eventId, gatewayPaymentId);
            }
            
            metrics.incrementWebhooksReceived(eventType);

        } catch (BusinessException e) {
            metrics.incrementWebhooksRejected("invalid_payload");
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            metrics.incrementWebhooksRejected("parse_error");
            throw new BusinessException("WEBHOOK_PARSE_ERROR", "Invalid webhook payload format");
        }

        // Step 3: Check for duplicate and create event record
        WebhookEvent webhookEvent = createWebhookEvent(
            eventId, eventType, gatewayPaymentId, gatewayRefundId, gatewayDisputeId, rawPayload, signature);

        // Step 4 & 5: Process business logic
        try {
            if (eventType.startsWith("payment.dispute.")) {
                processDisputeEvent(webhookEvent, eventType, gatewayDisputeId, gatewayPaymentId, eventData);
            } else if (eventType.startsWith("payment.refund.")) {
                processRefundEvent(webhookEvent, eventType, gatewayRefundId, gatewayPaymentId, eventData);
            } else {
                processPaymentEvent(webhookEvent, eventType, gatewayPaymentId, eventData);
            }
            
            webhookEvent.setStatus(WebhookStatus.PROCESSED);
            webhookEvent.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookEvent);
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordWebhookProcessingLatency(duration);
            
            log.info("Webhook processed successfully: event_id={}, duration_ms={}", eventId, duration);

        } catch (BusinessException e) {
            webhookEvent.setStatus(WebhookStatus.FAILED);
            webhookEvent.setFailureReason(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            metrics.incrementWebhooksProcessingErrors();
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing webhook: event_id={}", eventId, e);
            webhookEvent.setStatus(WebhookStatus.FAILED);
            webhookEvent.setFailureReason("Unexpected error: " + e.getMessage());
            webhookEventRepository.save(webhookEvent);
            metrics.incrementWebhooksProcessingErrors();
            throw new BusinessException("WEBHOOK_PROCESSING_ERROR", "Failed to process webhook");
        }
    }

    /**
     * Creates webhook event record with duplicate prevention.
     * Uses database unique constraint on event_id for race condition safety.
     */
    private WebhookEvent createWebhookEvent(
            String eventId, String eventType, String gatewayPaymentId, 
            String gatewayRefundId, String gatewayDisputeId, String payload, String signature) {
        
        try {
            WebhookEvent event = WebhookEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .gatewayName("RAZORPAY")
                .gatewayPaymentId(gatewayPaymentId)
                .gatewayRefundId(gatewayRefundId)
                .gatewayDisputeId(gatewayDisputeId)
                .payload(payload)
                .signature(signature)
                .status(WebhookStatus.VERIFIED)
                .receivedAt(LocalDateTime.now())
                .build();

            return webhookEventRepository.save(event);

        } catch (DataIntegrityViolationException e) {
            // Duplicate event - return existing
            log.info("Duplicate webhook event detected: event_id={}", eventId);
            metrics.incrementWebhooksDuplicate();
            WebhookEvent existing = webhookEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new BusinessException("WEBHOOK_DUPLICATE", 
                    "Webhook event already processed"));
            
            // Throw exception to indicate this is a duplicate
            throw new BusinessException("WEBHOOK_DUPLICATE", 
                "Webhook event " + eventId + " already processed");
        }
    }

    /**
     * Processes payment-related webhook events.
     */
    private void processPaymentEvent(
            WebhookEvent webhookEvent, String eventType, 
            String gatewayPaymentId, JsonNode eventData) {

        // Find payment by gateway ID
        Payment payment = paymentRepository.findByGatewayPaymentId(gatewayPaymentId)
            .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for gateway_payment_id: {}", gatewayPaymentId);
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setFailureReason("Payment not found in system");
            webhookEvent.setProcessedAt(LocalDateTime.now());
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found for gateway payment ID");
        }

        webhookEvent.setPayment(payment);
        webhookEvent.setStatus(WebhookStatus.PROCESSING);
        webhookEventRepository.save(webhookEvent);

        // Process based on event type
        boolean processed = false;
        switch (eventType) {
            case "payment.authorized":
                handlePaymentAuthorized(payment, eventData);
                processed = true;
                break;
            case "payment.captured":
                handlePaymentCaptured(payment, eventData);
                processed = true;
                break;
            case "payment.failed":
                handlePaymentFailed(payment, eventData);
                processed = true;
                break;
            default:
                log.info("Unsupported event type: {}", eventType);
                webhookEvent.setStatus(WebhookStatus.IGNORED);
                webhookEvent.setFailureReason("Unsupported event type");
                webhookEvent.setProcessedAt(LocalDateTime.now());
                throw new BusinessException("UNSUPPORTED_EVENT_TYPE", "Unsupported event type: " + eventType);
        }
        
        if (!processed) {
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setProcessedAt(LocalDateTime.now());
        }
    }

    /**
     * Handles payment.authorized event.
     */
    private void handlePaymentAuthorized(Payment payment, JsonNode eventData) {
        PaymentStatus currentStatus = payment.getStatus();
        PaymentStatus targetStatus = PaymentStatus.AUTHORIZED;

        // Check if already in target state (idempotent)
        if (currentStatus == targetStatus) {
            log.info("Payment {} already in AUTHORIZED state", payment.getPaymentId());
            return;
        }

        // Validate state machine transition
        try {
            stateMachine.validateTransition(currentStatus, targetStatus);
            payment.setStatus(targetStatus);
            paymentRepository.save(payment);
            
            // Create domain event (in same transaction)
            paymentEventService.createPaymentEvent(payment, PaymentEventType.PAYMENT_AUTHORIZED);
            
            log.info("Payment {} transitioned to AUTHORIZED", payment.getPaymentId());
        } catch (BusinessException e) {
            log.warn("Invalid state transition from {} to {} for payment {}: {}", 
                currentStatus, targetStatus, payment.getPaymentId(), e.getMessage());
            throw new BusinessException("WEBHOOK_INVALID_STATE", 
                "Cannot transition payment from " + currentStatus + " to " + targetStatus);
        }
    }

    /**
     * Handles payment.captured event.
     */
    private void handlePaymentCaptured(Payment payment, JsonNode eventData) {
        PaymentStatus currentStatus = payment.getStatus();
        PaymentStatus targetStatus = PaymentStatus.CAPTURED;

        // Check if already in target state (idempotent)
        if (currentStatus == targetStatus) {
            log.info("Payment {} already in CAPTURED state", payment.getPaymentId());
            return;
        }

        // Validate state machine transition
        try {
            stateMachine.validateTransition(currentStatus, targetStatus);
            payment.setStatus(targetStatus);
            paymentRepository.save(payment);
            
            // Create domain event (in same transaction)
            paymentEventService.createPaymentEvent(payment, PaymentEventType.PAYMENT_CAPTURED);
            
            log.info("Payment {} transitioned to CAPTURED", payment.getPaymentId());
        } catch (BusinessException e) {
            log.warn("Invalid state transition from {} to {} for payment {}: {}", 
                currentStatus, targetStatus, payment.getPaymentId(), e.getMessage());
            throw new BusinessException("WEBHOOK_INVALID_STATE", 
                "Cannot transition payment from " + currentStatus + " to " + targetStatus);
        }
    }

    /**
     * Handles payment.failed event.
     */
    private void handlePaymentFailed(Payment payment, JsonNode eventData) {
        PaymentStatus currentStatus = payment.getStatus();
        
        // Terminal states cannot transition to FAILED
        if (stateMachine.isTerminalState(currentStatus)) {
            log.warn("Payment {} is in terminal state {}, ignoring failed event", 
                payment.getPaymentId(), currentStatus);
            return;
        }

        PaymentStatus targetStatus = PaymentStatus.FAILED;

        // Check if already in FAILED state
        if (currentStatus == targetStatus) {
            log.info("Payment {} already in FAILED state", payment.getPaymentId());
            return;
        }

        // Extract failure reason
        String errorDescription = eventData.path("error_description").asText("Unknown error");
        payment.setStatus(targetStatus);
        payment.setFailureReason(errorDescription);
        paymentRepository.save(payment);
        
        // Create domain event (in same transaction)
        paymentEventService.createPaymentEvent(payment, PaymentEventType.PAYMENT_FAILED);
        
        log.info("Payment {} transitioned to FAILED: {}", payment.getPaymentId(), errorDescription);
    }

    /**
     * Processes refund-related webhook events.
     */
    private void processRefundEvent(
            WebhookEvent webhookEvent, String eventType, 
            String gatewayRefundId, String gatewayPaymentId, JsonNode eventData) {

        // Find refund by gateway refund ID
        Refund refund = refundRepository.findByGatewayRefundId(gatewayRefundId)
            .orElse(null);

        if (refund == null) {
            log.warn("Refund not found for gateway_refund_id: {}", gatewayRefundId);
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setFailureReason("Refund not found in system");
            webhookEvent.setProcessedAt(LocalDateTime.now());
            throw new BusinessException("REFUND_NOT_FOUND", "Refund not found for gateway refund ID");
        }

        webhookEvent.setRefund(refund);
        webhookEvent.setPayment(refund.getPayment());
        webhookEvent.setStatus(WebhookStatus.PROCESSING);
        webhookEventRepository.save(webhookEvent);

        // Process based on event type
        boolean processed = false;
        switch (eventType) {
            case "payment.refund.created":
                handleRefundCreated(refund, eventData);
                processed = true;
                break;
            case "payment.refund.processed":
                handleRefundProcessed(refund, eventData);
                processed = true;
                break;
            default:
                log.info("Unsupported refund event type: {}", eventType);
                webhookEvent.setStatus(WebhookStatus.IGNORED);
                webhookEvent.setFailureReason("Unsupported event type");
                webhookEvent.setProcessedAt(LocalDateTime.now());
                throw new BusinessException("UNSUPPORTED_EVENT_TYPE", "Unsupported event type: " + eventType);
        }
        
        if (!processed) {
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setProcessedAt(LocalDateTime.now());
        }
    }

    /**
     * Handles payment.refund.created event.
     * Transitions refund to PROCESSING state.
     */
    private void handleRefundCreated(Refund refund, JsonNode eventData) {
        RefundStatus currentStatus = refund.getStatus();
        RefundStatus targetStatus = RefundStatus.PROCESSING;

        log.info("Handling refund.created: refund_id={}, current_status={}", 
            refund.getRefundId(), currentStatus);

        // Already in PROCESSING or terminal state - idempotent
        if (currentStatus == targetStatus) {
            log.info("Refund {} already in PROCESSING state", refund.getRefundId());
            return;
        }

        // Do not regress from terminal states
        if (currentStatus == RefundStatus.SUCCEEDED || currentStatus == RefundStatus.FAILED) {
            log.warn("Cannot transition refund from terminal state {} to PROCESSING: refund_id={}", 
                currentStatus, refund.getRefundId());
            return;
        }

        // Transition to PROCESSING
        refund.setStatus(targetStatus);
        refundRepository.save(refund);
        
        log.info("Refund {} transitioned to PROCESSING", refund.getRefundId());
    }

    /**
     * Handles payment.refund.processed event.
     * Transitions refund to SUCCEEDED or FAILED based on status field.
     */
    private void handleRefundProcessed(Refund refund, JsonNode eventData) {
        RefundStatus currentStatus = refund.getStatus();
        
        log.info("Handling refund.processed: refund_id={}, current_status={}", 
            refund.getRefundId(), currentStatus);

        // Extract status from webhook payload
        String statusStr = eventData.path("status").asText();
        
        if ("processed".equalsIgnoreCase(statusStr)) {
            // Refund succeeded
            RefundStatus targetStatus = RefundStatus.SUCCEEDED;

            // Already succeeded - idempotent
            if (currentStatus == targetStatus) {
                log.info("Refund {} already in SUCCEEDED state", refund.getRefundId());
                return;
            }

            // Do not regress from FAILED
            if (currentStatus == RefundStatus.FAILED) {
                log.warn("Cannot transition failed refund to SUCCEEDED: refund_id={}", refund.getRefundId());
                return;
            }

            // Transition to SUCCEEDED
            refund.setStatus(targetStatus);
            refundRepository.save(refund);
            
            // Create REFUND_SUCCEEDED event (same transaction)
            // Consumer will create compensating ledger transaction
            refundEventService.createRefundEvent(refund, RefundEventType.REFUND_SUCCEEDED);
            
            log.info("Refund {} transitioned to SUCCEEDED", refund.getRefundId());

        } else if ("failed".equalsIgnoreCase(statusStr)) {
            // Refund failed
            RefundStatus targetStatus = RefundStatus.FAILED;

            // Already failed - idempotent
            if (currentStatus == targetStatus) {
                log.info("Refund {} already in FAILED state", refund.getRefundId());
                return;
            }

            // Do not regress from SUCCEEDED
            if (currentStatus == RefundStatus.SUCCEEDED) {
                log.warn("Cannot transition succeeded refund to FAILED: refund_id={}", refund.getRefundId());
                return;
            }

            // Extract failure reason
            String errorDescription = eventData.path("error_description").asText("Unknown error");
            
            refund.setStatus(targetStatus);
            refund.setFailureReason(errorDescription);
            refundRepository.save(refund);
            
            // Create REFUND_FAILED event (same transaction)
            refundEventService.createRefundEvent(refund, RefundEventType.REFUND_FAILED);
            
            log.info("Refund {} transitioned to FAILED: {}", refund.getRefundId(), errorDescription);

        } else {
            log.warn("Unknown refund status in webhook: {}", statusStr);
            throw new BusinessException("WEBHOOK_INVALID_STATUS", "Unknown refund status: " + statusStr);
        }
    }

    /**
     * Processes dispute-related webhook events.
     */
    private void processDisputeEvent(
            WebhookEvent webhookEvent, String eventType, 
            String gatewayDisputeId, String gatewayPaymentId, JsonNode eventData) {

        // Find dispute by gateway dispute ID
        Dispute dispute = disputeRepository.findByGatewayDisputeId(gatewayDisputeId)
            .orElse(null);

        if (dispute == null) {
            log.warn("Dispute not found for gateway_dispute_id: {}", gatewayDisputeId);
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setFailureReason("Dispute not found in system");
            webhookEvent.setProcessedAt(LocalDateTime.now());
            throw new BusinessException("DISPUTE_NOT_FOUND", "Dispute not found for gateway dispute ID");
        }

        webhookEvent.setDispute(dispute);
        webhookEvent.setPayment(dispute.getPayment());
        webhookEvent.setStatus(WebhookStatus.PROCESSING);
        webhookEventRepository.save(webhookEvent);

        // Process based on event type
        boolean processed = false;
        switch (eventType) {
            case "payment.dispute.created":
                handleDisputeCreated(dispute, eventData);
                processed = true;
                break;
            case "payment.dispute.won":
                handleDisputeWon(dispute, eventData);
                processed = true;
                break;
            case "payment.dispute.lost":
                handleDisputeLost(dispute, eventData);
                processed = true;
                break;
            case "payment.dispute.closed":
                handleDisputeClosed(dispute, eventData);
                processed = true;
                break;
            default:
                log.info("Unsupported dispute event type: {}", eventType);
                webhookEvent.setStatus(WebhookStatus.IGNORED);
                webhookEvent.setFailureReason("Unsupported event type");
                webhookEvent.setProcessedAt(LocalDateTime.now());
                throw new BusinessException("UNSUPPORTED_EVENT_TYPE", "Unsupported event type: " + eventType);
        }
        
        if (!processed) {
            webhookEvent.setStatus(WebhookStatus.IGNORED);
            webhookEvent.setProcessedAt(LocalDateTime.now());
        }
    }

    private void handleDisputeCreated(Dispute dispute, JsonNode eventData) {
        DisputeStatus currentStatus = dispute.getStatus();
        DisputeStatus targetStatus = DisputeStatus.EVIDENCE_REQUIRED;

        log.info("Handling dispute.created: dispute_id={}, current_status={}", 
            dispute.getDisputeId(), currentStatus);

        // Idempotent - already in correct state
        if (currentStatus == targetStatus) {
            log.info("Dispute {} already in EVIDENCE_REQUIRED state", dispute.getDisputeId());
            return;
        }

        // Do not regress from terminal states
        if (currentStatus == DisputeStatus.WON || currentStatus == DisputeStatus.LOST || 
            currentStatus == DisputeStatus.CLOSED) {
            log.warn("Cannot transition dispute from terminal state {} to EVIDENCE_REQUIRED: dispute_id={}", 
                currentStatus, dispute.getDisputeId());
            return;
        }

        // Transition to EVIDENCE_REQUIRED
        dispute.setStatus(targetStatus);
        disputeRepository.save(dispute);
        
        // Create event
        disputeEventService.createDisputeEvent(dispute, DisputeEventType.DISPUTE_EVIDENCE_REQUIRED);
        
        log.info("Dispute {} transitioned to EVIDENCE_REQUIRED", dispute.getDisputeId());
    }

    private void handleDisputeWon(Dispute dispute, JsonNode eventData) {
        DisputeStatus currentStatus = dispute.getStatus();
        DisputeStatus targetStatus = DisputeStatus.WON;

        log.info("Handling dispute.won: dispute_id={}, current_status={}", 
            dispute.getDisputeId(), currentStatus);

        // Idempotent - already won
        if (currentStatus == targetStatus) {
            log.info("Dispute {} already in WON state", dispute.getDisputeId());
            return;
        }

        // Do not regress from other terminal states
        if (currentStatus == DisputeStatus.LOST || currentStatus == DisputeStatus.CLOSED) {
            log.warn("Cannot transition dispute from terminal state {} to WON: dispute_id={}", 
                currentStatus, dispute.getDisputeId());
            return;
        }

        // Transition to WON
        dispute.setStatus(targetStatus);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        
        // Create event (consumer will handle ledger)
        disputeEventService.createDisputeEvent(dispute, DisputeEventType.DISPUTE_WON);
        
        log.info("Dispute {} transitioned to WON", dispute.getDisputeId());
    }

    private void handleDisputeLost(Dispute dispute, JsonNode eventData) {
        DisputeStatus currentStatus = dispute.getStatus();
        DisputeStatus targetStatus = DisputeStatus.LOST;

        log.info("Handling dispute.lost: dispute_id={}, current_status={}", 
            dispute.getDisputeId(), currentStatus);

        // Idempotent - already lost
        if (currentStatus == targetStatus) {
            log.info("Dispute {} already in LOST state", dispute.getDisputeId());
            return;
        }

        // Do not regress from other terminal states
        if (currentStatus == DisputeStatus.WON || currentStatus == DisputeStatus.CLOSED) {
            log.warn("Cannot transition dispute from terminal state {} to LOST: dispute_id={}", 
                currentStatus, dispute.getDisputeId());
            return;
        }

        // Transition to LOST
        dispute.setStatus(targetStatus);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        
        // Create event (consumer will handle ledger)
        disputeEventService.createDisputeEvent(dispute, DisputeEventType.DISPUTE_LOST);
        
        log.info("Dispute {} transitioned to LOST", dispute.getDisputeId());
    }

    private void handleDisputeClosed(Dispute dispute, JsonNode eventData) {
        DisputeStatus currentStatus = dispute.getStatus();
        DisputeStatus targetStatus = DisputeStatus.CLOSED;

        log.info("Handling dispute.closed: dispute_id={}, current_status={}", 
            dispute.getDisputeId(), currentStatus);

        // Idempotent - already closed
        if (currentStatus == targetStatus) {
            log.info("Dispute {} already in CLOSED state", dispute.getDisputeId());
            return;
        }

        // Do not regress from WON or LOST
        if (currentStatus == DisputeStatus.WON || currentStatus == DisputeStatus.LOST) {
            log.warn("Cannot transition dispute from terminal state {} to CLOSED: dispute_id={}", 
                currentStatus, dispute.getDisputeId());
            return;
        }

        // Transition to CLOSED
        dispute.setStatus(targetStatus);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        
        // Create event
        disputeEventService.createDisputeEvent(dispute, DisputeEventType.DISPUTE_CLOSED);
        
        log.info("Dispute {} transitioned to CLOSED", dispute.getDisputeId());
    }
}
