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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerDisputeTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private LedgerAccountRepository accountRepository;

    private static final Long MERCHANT_ID = 1L;
    private static final String PAYMENT_ID = "PAY_123";
    private static final String DISPUTE_ID = "DSP_456";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final String CURRENCY = "USD";

    @BeforeEach
    void setUp() {
        // Create capture first (prerequisite for dispute)
        ledgerService.recordPaymentCapture(PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);
    }

    @Test
    void shouldRecordDisputeLoss() {
        LedgerTransaction transaction = ledgerService.recordDisputeLoss(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getTransactionType()).isEqualTo("DISPUTE_LOSS");
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(transaction.getTransactionReference()).isEqualTo("DISPUTE_LOSS_" + DISPUTE_ID);

        // Verify entries
        List<LedgerEntry> entries = entryRepository.findByTransactionId(transaction.getId());
        assertThat(entries).hasSize(2);

        // Verify debit to DISPUTE_LOSS account
        LedgerEntry debit = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT)
            .findFirst()
            .orElseThrow();
        assertThat(debit.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(debit.getAccount().getAccountType()).isEqualTo(AccountType.DISPUTE_LOSS);

        // Verify credit to MERCHANT_RECEIVABLE account
        LedgerEntry credit = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .findFirst()
            .orElseThrow();
        assertThat(credit.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(credit.getAccount().getAccountType()).isEqualTo(AccountType.MERCHANT_RECEIVABLE);

        // Verify balance
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
    }

    @Test
    void shouldBeIdempotentForDisputeLoss() {
        LedgerTransaction first = ledgerService.recordDisputeLoss(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        LedgerTransaction second = ledgerService.recordDisputeLoss(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getTransactionReference()).isEqualTo(second.getTransactionReference());

        // Verify only one transaction created
        long count = transactionRepository.findAll().stream()
            .filter(t -> t.getTransactionType().equals("DISPUTE_LOSS"))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldRecordDisputeWin() {
        LedgerTransaction transaction = ledgerService.recordDisputeWin(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getTransactionType()).isEqualTo("DISPUTE_WIN");
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(transaction.getTransactionReference()).isEqualTo("DISPUTE_WIN_" + DISPUTE_ID);

        // Verify NO entries (audit only)
        List<LedgerEntry> entries = entryRepository.findByTransactionId(transaction.getId());
        assertThat(entries).isEmpty();
    }

    @Test
    void shouldBeIdempotentForDisputeWin() {
        LedgerTransaction first = ledgerService.recordDisputeWin(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        LedgerTransaction second = ledgerService.recordDisputeWin(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        assertThat(first.getId()).isEqualTo(second.getId());
    }

    @Test
    void shouldRejectInvalidAmount() {
        assertThatThrownBy(() -> ledgerService.recordDisputeLoss(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, new BigDecimal("-10.00"), CURRENCY))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> ledgerService.recordDisputeLoss(
            DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, BigDecimal.ZERO, CURRENCY))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    void shouldCreateDisputeLossAccount() {
        ledgerService.recordDisputeLoss(DISPUTE_ID, PAYMENT_ID, MERCHANT_ID, AMOUNT, CURRENCY);

        LedgerAccount account = accountRepository
            .findByMerchantIdAndAccountTypeAndCurrency(MERCHANT_ID, AccountType.DISPUTE_LOSS, CURRENCY)
            .orElseThrow();

        assertThat(account).isNotNull();
        assertThat(account.getAccountType()).isEqualTo(AccountType.DISPUTE_LOSS);
        assertThat(account.getCurrency()).isEqualTo(CURRENCY);
        assertThat(account.getMerchantId()).isEqualTo(MERCHANT_ID);
    }
}
