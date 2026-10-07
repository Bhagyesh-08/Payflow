package com.payflow.webhook.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook Controller
 * 
 * Handles incoming webhooks from payment gateways.
 * Keeps controller thin - delegates to service layer.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * Razorpay webhook endpoint.
     * 
     * Signature verification requires exact raw request body.
     * Controller receives body as String to preserve exact bytes.
     * 
     * @param rawPayload the raw webhook payload (exact bytes)
     * @param signature the X-Razorpay-Signature header
     * @return 200 if processed successfully, 202 if duplicate, 400/401 for errors
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Received Razorpay webhook");

        try {
            webhookService.processWebhook(rawPayload, signature);
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (BusinessException e) {
            if ("WEBHOOK_DUPLICATE".equals(e.getErrorCode())) {
                log.info("Duplicate webhook received");
                return ResponseEntity.status(HttpStatus.OK).body("Webhook already processed");
            }
            
            if ("WEBHOOK_SIGNATURE_MISSING".equals(e.getErrorCode()) || 
                "WEBHOOK_SIGNATURE_INVALID".equals(e.getErrorCode())) {
                log.error("Webhook signature verification failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }

            log.error("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook processing failed");
        }
    }
}
