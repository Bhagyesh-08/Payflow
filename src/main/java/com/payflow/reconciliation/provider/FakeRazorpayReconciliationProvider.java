package com.payflow.reconciliation.provider;

import com.payflow.reconciliation.model.GatewayPaymentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulated / Fake Razorpay Reconciliation Provider.
 * 
 * Generates synthetic records for local development, testing, and demonstration.
 * 
 * IMPORTANT: Razorpay Java SDK 1.4.6 does not expose reconciliation or settlement
 * APIs suitable for direct programmatic query. In production, settlement files
 * (CSV/SFTP) must be ingested via scheduled batch jobs.
 * 
 * This provider is active when 'razorpay.reconciliation.fake-mode=true' (default: true).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "razorpay.reconciliation.fake-mode", havingValue = "true", matchIfMissing = true)
public class FakeRazorpayReconciliationProvider implements ReconciliationDataProvider {

    @Override
    public List<GatewayPaymentRecord> getPaymentRecords(Long merchantId, LocalDate businessDate) {
        log.warn("USING SYNTHETIC RECONCILIATION PROVIDER: Razorpay SDK 1.4.6 does not provide reconciliation APIs. " +
                 "Returning synthetic test records for merchant {} on {}", merchantId, businessDate);
        
        // Return deterministic fake data for testing
        List<GatewayPaymentRecord> records = new ArrayList<>();
        
        // Simulated gateway records - would come from real Razorpay API
        records.add(GatewayPaymentRecord.builder()
            .gateway("RAZORPAY")
            .gatewayReference("pay_fake_001")
            .merchantReference("PAY_MATCH_001")
            .amount(new BigDecimal("1000.00"))
            .currency("INR")
            .status("captured")
            .capturedAt(businessDate.atTime(10, 0))
            .createdAt(businessDate.atTime(9, 30))
            .build());
        
        records.add(GatewayPaymentRecord.builder()
            .gateway("RAZORPAY")
            .gatewayReference("pay_fake_002")
            .merchantReference("PAY_AMOUNT_MISMATCH")
            .amount(new BigDecimal("500.00"))
            .currency("INR")
            .status("captured")
            .capturedAt(businessDate.atTime(11, 0))
            .createdAt(businessDate.atTime(10, 30))
            .build());
        
        records.add(GatewayPaymentRecord.builder()
            .gateway("RAZORPAY")
            .gatewayReference("pay_fake_003")
            .merchantReference(null) // Missing internal payment
            .amount(new BigDecimal("750.00"))
            .currency("INR")
            .status("captured")
            .capturedAt(businessDate.atTime(12, 0))
            .createdAt(businessDate.atTime(11, 30))
            .build());
        
        log.info("Fake Razorpay Provider: Returning {} records", records.size());
        return records;
    }

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
