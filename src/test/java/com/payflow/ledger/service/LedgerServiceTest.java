package com.payflow.ledger.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.ledger.entity.LedgerAccount;
import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.enums.AccountType;
import com.payflow.ledger.enums.EntryType;
import com.payflow.ledger.repository.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerAccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    @Mock
    private com.payflow.common.observability.BusinessMetrics metrics;

    @InjectMocks
    private LedgerService ledgerService;

    private LedgerAccount customerAccount;
    private LedgerAccount merchantAccount;

    @BeforeEach
    void setUp() {
        customerAccount = LedgerAccount.builder()
            .id(1L)
            .merchantId(100L)
            .accountCode("ACC_100_CUSTOMER_PAYMENT_INR")
            .accountType(AccountType.CUSTOMER_PAYMENT)
            .currency("INR")
            .build();

        merchantAccount = LedgerAccount.builder()
            .id(2L)
            .merchantId(100L)
            .accountCode("ACC_100_MERCHANT_RECEIVABLE_INR")
            .accountType(AccountType.MERCHANT_RECEIVABLE)
            .currency("INR")
            .build();
    }

    @Test
    void recordPaymentCapture_Success_CreatesBalancedTransaction() {
        // Arrange
        String paymentId = "PAY_123";
        Long merchantId = 100L;
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "INR";

        when(transactionRepository.existsByTransactionReference(any())).thenReturn(false);
        when(accountRepository.findByMerchantIdAndAccountTypeAndCurrency(merchantId, AccountType.CUSTOMER_PAYMENT, currency))
            .thenReturn(Optional.of(customerAccount));
        when(accountRepository.findByMerchantIdAndAccountTypeAndCurrency(merchantId, AccountType.MERCHANT_RECEIVABLE, currency))
            .thenReturn(Optional.of(merchantAccount));

        LedgerTransaction savedTransaction = LedgerTransaction.builder()
            .id(1L)
            .transactionReference("PAYMENT_CAPTURE_" + paymentId)
            .paymentId(paymentId)
            .merchantId(merchantId)
            .amount(amount)
            .currency(currency)
            .build();

        when(transactionRepository.save(any(LedgerTransaction.class))).thenReturn(savedTransaction);
        when(entryRepository.saveAll(argThat((List<LedgerEntry> entries) -> 
            entries.size() == 2 && 
            entries.stream().anyMatch(e -> e.getEntryType() == EntryType.DEBIT) &&
            entries.stream().anyMatch(e -> e.getEntryType() == EntryType.CREDIT)
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(entryRepository.sumAmountByTransactionIdAndEntryType(1L, EntryType.DEBIT)).thenReturn(amount);
        when(entryRepository.sumAmountByTransactionIdAndEntryType(1L, EntryType.CREDIT)).thenReturn(amount);

        // Act
        LedgerTransaction result = ledgerService.recordPaymentCapture(paymentId, merchantId, amount, currency);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isEqualTo(paymentId);
        verify(transactionRepository).save(any(LedgerTransaction.class));
        verify(entryRepository).saveAll(any());
    }

    @Test
    void recordPaymentCapture_ZeroAmount_ThrowsException() {
        assertThatThrownBy(() -> ledgerService.recordPaymentCapture("PAY_123", 100L, BigDecimal.ZERO, "INR"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    void recordPaymentCapture_NegativeAmount_ThrowsException() {
        assertThatThrownBy(() -> ledgerService.recordPaymentCapture("PAY_123", 100L, new BigDecimal("-100"), "INR"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    void recordPaymentCapture_NullAmount_ThrowsException() {
        assertThatThrownBy(() -> ledgerService.recordPaymentCapture("PAY_123", 100L, null, "INR"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    void recordPaymentCapture_InvalidCurrency_ThrowsException() {
        assertThatThrownBy(() -> ledgerService.recordPaymentCapture("PAY_123", 100L, new BigDecimal("100"), "INVALID"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid currency code");
    }

    @Test
    void recordPaymentCapture_IdempotentDuplicate_ReturnsExisting() {
        // Arrange
        String paymentId = "PAY_123";
        String transactionRef = "PAYMENT_CAPTURE_" + paymentId;

        LedgerTransaction existingTransaction = LedgerTransaction.builder()
            .id(1L)
            .transactionReference(transactionRef)
            .paymentId(paymentId)
            .build();

        when(transactionRepository.existsByTransactionReference(transactionRef)).thenReturn(true);
        when(transactionRepository.findByTransactionReference(transactionRef))
            .thenReturn(Optional.of(existingTransaction));

        // Act
        LedgerTransaction result = ledgerService.recordPaymentCapture(paymentId, 100L, new BigDecimal("1000"), "INR");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getByPaymentId_Found_ReturnsTransaction() {
        // Arrange
        LedgerTransaction transaction = LedgerTransaction.builder()
            .id(1L)
            .paymentId("PAY_123")
            .build();

        when(transactionRepository.findByPaymentId("PAY_123")).thenReturn(Optional.of(transaction));

        // Act
        LedgerTransaction result = ledgerService.getByPaymentId("PAY_123");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isEqualTo("PAY_123");
    }

    @Test
    void getByPaymentId_NotFound_ThrowsException() {
        when(transactionRepository.findByPaymentId("PAY_999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.getByPaymentId("PAY_999"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Ledger transaction not found");
    }
}
