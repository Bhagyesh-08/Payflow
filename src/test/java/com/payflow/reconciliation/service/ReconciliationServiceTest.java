package com.payflow.reconciliation.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.entity.ReconciliationRun;
import com.payflow.reconciliation.enums.DiscrepancyType;
import com.payflow.reconciliation.enums.ReconciliationResult;
import com.payflow.reconciliation.model.GatewayPaymentRecord;
import com.payflow.reconciliation.provider.ReconciliationDataProvider;
import com.payflow.reconciliation.repository.ReconciliationRecordRepository;
import com.payflow.reconciliation.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationRecordRepository recordRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReconciliationDataProvider dataProvider;

    @InjectMocks
    private ReconciliationService reconciliationService;

    private Merchant merchant;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
            .id(1L)
            .businessName("Test Merchant")
            .build();
        
        businessDate = LocalDate.of(2026, 9, 29);
    }

    @Test
    void startReconciliationRun_ExactMatch_CreatesMatchedRecord() {
        // Arrange
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(any(), any(), any()))
            .thenReturn(Optional.empty());
        
        GatewayPaymentRecord gatewayRecord = createGatewayRecord("pay_001", "PAY_001", "1000.00", "INR", "captured");
        when(dataProvider.getPaymentRecords(1L, businessDate)).thenReturn(Arrays.asList(gatewayRecord));

        Payment payment = createPayment("PAY_001", "pay_001", "1000.00", "INR", PaymentStatus.CAPTURED);
        when(paymentRepository.findByGatewayPaymentId("pay_001")).thenReturn(Optional.of(payment));

        ReconciliationRun savedRun = ReconciliationRun.builder().id(1L).build();
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalRecords()).isEqualTo(1);
        assertThat(result.getMatchedRecords()).isEqualTo(1);
        assertThat(result.getDiscrepancyRecords()).isEqualTo(0);
        verify(recordRepository).saveAll(argThat((List<ReconciliationRecord> records) -> 
            records.size() == 1 && 
            records.get(0).getResult() == ReconciliationResult.MATCHED &&
            records.get(0).getDiscrepancyType() == DiscrepancyType.NONE
        ));
    }

    @Test
    void startReconciliationRun_AmountMismatch_CreatesDiscrepancyRecord() {
        // Arrange
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(any(), any(), any()))
            .thenReturn(Optional.empty());
        
        GatewayPaymentRecord gatewayRecord = createGatewayRecord("pay_002", "PAY_002", "1000.00", "INR", "captured");
        when(dataProvider.getPaymentRecords(1L, businessDate)).thenReturn(Arrays.asList(gatewayRecord));

        Payment payment = createPayment("PAY_002", "pay_002", "950.00", "INR", PaymentStatus.CAPTURED);
        when(paymentRepository.findByGatewayPaymentId("pay_002")).thenReturn(Optional.of(payment));

        ReconciliationRun savedRun = ReconciliationRun.builder().id(1L).build();
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result.getTotalRecords()).isEqualTo(1);
        assertThat(result.getMatchedRecords()).isEqualTo(0);
        assertThat(result.getDiscrepancyRecords()).isEqualTo(1);
        verify(recordRepository).saveAll(argThat((List<ReconciliationRecord> records) -> 
            records.size() == 1 && 
            records.get(0).getResult() == ReconciliationResult.DISCREPANCY &&
            records.get(0).getDiscrepancyType() == DiscrepancyType.AMOUNT_MISMATCH
        ));
    }

    @Test
    void startReconciliationRun_CurrencyMismatch_CreatesDiscrepancyRecord() {
        // Arrange
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(any(), any(), any()))
            .thenReturn(Optional.empty());
        
        GatewayPaymentRecord gatewayRecord = createGatewayRecord("pay_003", "PAY_003", "1000.00", "USD", "captured");
        when(dataProvider.getPaymentRecords(1L, businessDate)).thenReturn(Arrays.asList(gatewayRecord));

        Payment payment = createPayment("PAY_003", "pay_003", "1000.00", "INR", PaymentStatus.CAPTURED);
        when(paymentRepository.findByGatewayPaymentId("pay_003")).thenReturn(Optional.of(payment));

        ReconciliationRun savedRun = ReconciliationRun.builder().id(1L).build();
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result.getDiscrepancyRecords()).isEqualTo(1);
        verify(recordRepository).saveAll(argThat((List<ReconciliationRecord> records) -> 
            records.get(0).getDiscrepancyType() == DiscrepancyType.CURRENCY_MISMATCH
        ));
    }

    @Test
    void startReconciliationRun_MissingInternalPayment_CreatesDiscrepancyRecord() {
        // Arrange
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(any(), any(), any()))
            .thenReturn(Optional.empty());
        
        GatewayPaymentRecord gatewayRecord = createGatewayRecord("pay_999", null, "1000.00", "INR", "captured");
        when(dataProvider.getPaymentRecords(1L, businessDate)).thenReturn(Arrays.asList(gatewayRecord));

        when(paymentRepository.findByGatewayPaymentId("pay_999")).thenReturn(Optional.empty());

        ReconciliationRun savedRun = ReconciliationRun.builder().id(1L).build();
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result.getDiscrepancyRecords()).isEqualTo(1);
        verify(recordRepository).saveAll(argThat((List<ReconciliationRecord> records) -> 
            records.get(0).getDiscrepancyType() == DiscrepancyType.MISSING_INTERNAL_PAYMENT &&
            records.get(0).getPaymentId() == null
        ));
    }

    @Test
    void startReconciliationRun_StatusMismatch_CreatesDiscrepancyRecord() {
        // Arrange
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(any(), any(), any()))
            .thenReturn(Optional.empty());
        
        GatewayPaymentRecord gatewayRecord = createGatewayRecord("pay_004", "PAY_004", "1000.00", "INR", "failed");
        when(dataProvider.getPaymentRecords(1L, businessDate)).thenReturn(Arrays.asList(gatewayRecord));

        Payment payment = createPayment("PAY_004", "pay_004", "1000.00", "INR", PaymentStatus.CAPTURED);
        when(paymentRepository.findByGatewayPaymentId("pay_004")).thenReturn(Optional.of(payment));

        ReconciliationRun savedRun = ReconciliationRun.builder().id(1L).build();
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result.getDiscrepancyRecords()).isEqualTo(1);
        verify(recordRepository).saveAll(argThat((List<ReconciliationRecord> records) -> 
            records.get(0).getDiscrepancyType() == DiscrepancyType.STATUS_MISMATCH
        ));
    }

    @Test
    void startReconciliationRun_ProviderUnavailable_ThrowsException() {
        when(dataProvider.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("not available");
    }

    @Test
    void startReconciliationRun_ExistingRun_ReturnsExisting() {
        // Arrange
        ReconciliationRun existingRun = ReconciliationRun.builder().id(100L).build();
        when(dataProvider.isAvailable()).thenReturn(true);
        when(runRepository.findByMerchantIdAndGatewayAndBusinessDate(1L, "RAZORPAY", businessDate))
            .thenReturn(Optional.of(existingRun));

        // Act
        ReconciliationRun result = reconciliationService.startReconciliationRun(1L, "RAZORPAY", businessDate);

        // Assert
        assertThat(result.getId()).isEqualTo(100L);
        verify(dataProvider, never()).getPaymentRecords(any(), any());
    }

    private GatewayPaymentRecord createGatewayRecord(String gatewayRef, String merchantRef, 
            String amount, String currency, String status) {
        return GatewayPaymentRecord.builder()
            .gateway("RAZORPAY")
            .gatewayReference(gatewayRef)
            .merchantReference(merchantRef)
            .amount(new BigDecimal(amount))
            .currency(currency)
            .status(status)
            .build();
    }

    private Payment createPayment(String paymentId, String gatewayPaymentId, 
            String amount, String currency, PaymentStatus status) {
        return Payment.builder()
            .paymentId(paymentId)
            .gatewayPaymentId(gatewayPaymentId)
            .merchant(merchant)
            .amount(new BigDecimal(amount))
            .currency(currency)
            .status(status)
            .build();
    }
}
