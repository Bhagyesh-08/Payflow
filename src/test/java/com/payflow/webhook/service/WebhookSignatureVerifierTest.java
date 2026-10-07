package com.payflow.webhook.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.infrastructure.gateway.razorpay.RazorpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSignatureVerifierTest {

    @Mock
    private RazorpayProperties razorpayProperties;

    private WebhookSignatureVerifier verifier;

    private static final String TEST_SECRET = "test_webhook_secret_123";
    private static final String TEST_PAYLOAD = "{\"event\":\"payment.captured\",\"id\":\"evt_123\"}";

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier(razorpayProperties);
    }

    @Test
    void verifySignature_ValidSignature_Success() {
        // Arrange
        when(razorpayProperties.getWebhookSecret()).thenReturn(TEST_SECRET);
        
        // Generate valid signature
        String validSignature = generateSignature(TEST_PAYLOAD, TEST_SECRET);

        // Act & Assert
        assertThatCode(() -> verifier.verifySignature(TEST_PAYLOAD, validSignature))
            .doesNotThrowAnyException();
    }

    @Test
    void verifySignature_InvalidSignature_ThrowsException() {
        // Arrange
        when(razorpayProperties.getWebhookSecret()).thenReturn(TEST_SECRET);
        String invalidSignature = "invalid_signature_12345";

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(TEST_PAYLOAD, invalidSignature))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_INVALID");
    }

    @Test
    void verifySignature_MissingSignature_ThrowsException() {
        // Arrange - no stubbing needed as signature is null

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(TEST_PAYLOAD, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_MISSING");
    }

    @Test
    void verifySignature_EmptySignature_ThrowsException() {
        // Arrange - no stubbing needed as signature is empty

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(TEST_PAYLOAD, ""))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_MISSING");
    }

    @Test
    void verifySignature_ModifiedPayload_ThrowsException() {
        // Arrange
        when(razorpayProperties.getWebhookSecret()).thenReturn(TEST_SECRET);
        String validSignature = generateSignature(TEST_PAYLOAD, TEST_SECRET);
        String modifiedPayload = TEST_PAYLOAD + " ";

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(modifiedPayload, validSignature))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_INVALID");
    }

    @Test
    void verifySignature_MissingWebhookSecret_ThrowsException() {
        // Arrange
        when(razorpayProperties.getWebhookSecret()).thenReturn(null);
        String signature = "some_signature";

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(TEST_PAYLOAD, signature))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SECRET_NOT_CONFIGURED");
    }

    @Test
    void verifySignature_DifferentSecret_ThrowsException() {
        // Arrange
        when(razorpayProperties.getWebhookSecret()).thenReturn("different_secret");
        String signatureWithDifferentSecret = generateSignature(TEST_PAYLOAD, TEST_SECRET);

        // Act & Assert
        assertThatThrownBy(() -> verifier.verifySignature(TEST_PAYLOAD, signatureWithDifferentSecret))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_INVALID");
    }

    /**
     * Helper method to generate HMAC SHA256 signature for testing.
     */
    private String generateSignature(String data, String key) {
        try {
            javax.crypto.Mac sha256Hmac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = 
                new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(signedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
