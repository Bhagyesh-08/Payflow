package com.payflow.webhook.repository;

import com.payflow.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByEventId(String eventId);
    Optional<WebhookEvent> findByGatewayPaymentId(String gatewayPaymentId);
}
