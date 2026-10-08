package com.payflow.ledger.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.common.util.IdGenerator;
import com.payflow.ledger.entity.LedgerAccount;
import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.enums.AccountType;
import com.payflow.ledger.enums.EntryType;
import com.payflow.ledger.repository.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Ledger Service.
 * 
 * Manages immutable double-entry accounting ledger.
 * 
 * Core Invariant: Total Debits = Total Credits
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAccountRepository accountRepository;
    private final BusinessMetrics metrics;
    private final LedgerEntryRepository entryRepository;

    /**
     * Records a payment capture in the ledger.
     * 
     * Creates balanced double-entry transaction:
     * DEBIT   CUSTOMER_PAYMENT     amount
     * CREDIT  MERCHANT_RECEIVABLE  amount
     * 
     * Idempotent: Uses transaction_reference based on payment_id.
     * 
     * @param paymentId Payment identifier
     * @param merchantId Merchant identifier
     * @param amount Payment amount
     * @param currency Payment currency
     * @return Created ledger transaction
     * @throws BusinessException if validation fails
     */
    @Transactional
    public LedgerTransaction recordPaymentCapture(
            String paymentId, 
            Long merchantId, 
            BigDecimal amount, 
            String currency) {

        validateAmount(amount);
        validateCurrency(currency);

        String transactionReference = "PAYMENT_CAPTURE_" + paymentId;

        // Idempotency check
        if (transactionRepository.existsByTransactionReference(transactionReference)) {
            log.info("Ledger transaction already exists: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("CONCURRENT_CREATION", "Concurrent ledger creation race"));
        }

        try {
            // Get or create accounts
            LedgerAccount customerPaymentAccount = getOrCreateAccount(
                merchantId, AccountType.CUSTOMER_PAYMENT, currency);
            LedgerAccount merchantReceivableAccount = getOrCreateAccount(
                merchantId, AccountType.MERCHANT_RECEIVABLE, currency);

            // Create transaction
            LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionReference(transactionReference)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .transactionType("PAYMENT_CAPTURE")
                .currency(currency)
                .amount(amount)
                .description("Payment captured: " + paymentId)
                .build();

            transaction = transactionRepository.save(transaction);

            // Create entries
            List<LedgerEntry> entries = new ArrayList<>();

            entries.add(createEntry(transaction, customerPaymentAccount, 
                EntryType.DEBIT, amount, currency, "Payment from customer"));

            entries.add(createEntry(transaction, merchantReceivableAccount, 
                EntryType.CREDIT, amount, currency, "Amount receivable by merchant"));

            entries = entryRepository.saveAll(entries);

            // Validate balance
            validateBalance(transaction.getId());

            log.info("Ledger transaction created: reference={}, amount={} {}", 
                transactionReference, amount, currency);
            
            metrics.incrementLedgerTransactionsCreated();

            return transaction;

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate ledger transaction detected: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("LEDGER_CREATION_FAILED", "Failed to create ledger transaction"));
        } catch (Exception e) {
            log.error("Failed to create ledger transaction: reference={}", transactionReference, e);
            metrics.incrementLedgerTransactionsFailed();
            throw e;
        }
    }

    /**
     * Retrieves ledger transaction by payment ID.
     */
    @Transactional(readOnly = true)
    public LedgerTransaction getByPaymentId(String paymentId) {
        return transactionRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new BusinessException("LEDGER_NOT_FOUND", "Ledger transaction not found for payment: " + paymentId));
    }

    /**
     * Records a refund in the ledger.
     * 
     * Creates compensating transaction:
     * DEBIT   MERCHANT_RECEIVABLE  amount
     * CREDIT  CUSTOMER_REFUND      amount
     * 
     * CRITICAL: Original payment ledger transaction remains unchanged.
     */
    @Transactional
    public LedgerTransaction recordRefund(
            String refundId,
            String paymentId,
            Long merchantId,
            BigDecimal amount,
            String currency) {

        validateAmount(amount);
        validateCurrency(currency);

        String transactionReference = "REFUND_" + refundId;

        // Idempotency check
        if (transactionRepository.existsByTransactionReference(transactionReference)) {
            log.info("Ledger transaction already exists: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("CONCURRENT_CREATION", "Concurrent ledger creation race"));
        }

        try {
            // Get or create accounts
            LedgerAccount merchantReceivableAccount = getOrCreateAccount(
                merchantId, AccountType.MERCHANT_RECEIVABLE, currency);
            LedgerAccount customerRefundAccount = getOrCreateAccount(
                merchantId, AccountType.CUSTOMER_REFUND, currency);

            // Create transaction
            LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionReference(transactionReference)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .transactionType("REFUND")
                .currency(currency)
                .amount(amount)
                .description("Refund: " + refundId)
                .build();

            transaction = transactionRepository.save(transaction);

            // Create entries
            List<LedgerEntry> entries = new ArrayList<>();

            entries.add(createEntry(transaction, merchantReceivableAccount,
                EntryType.DEBIT, amount, currency, "Refund reduces merchant receivable"));

            entries.add(createEntry(transaction, customerRefundAccount,
                EntryType.CREDIT, amount, currency, "Refund to customer"));

            entries = entryRepository.saveAll(entries);

            // Validate balance
            validateBalance(transaction.getId());

            log.info("Refund ledger transaction created: reference={}, amount={} {}", 
                transactionReference, amount, currency);

            return transaction;

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate refund ledger transaction detected: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("LEDGER_CREATION_FAILED", "Failed to create ledger transaction"));
        }
    }

    /**
     * Retrieves all ledger transactions for a merchant.
     */
    @Transactional(readOnly = true)
    public List<LedgerTransaction> getByMerchantId(Long merchantId) {
        return transactionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Retrieves paginated ledger transactions for a merchant.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LedgerTransaction> getByMerchantId(
            Long merchantId, 
            org.springframework.data.domain.Pageable pageable) {
        return transactionRepository.findByMerchantId(merchantId, pageable);
    }

    /**
     * Retrieves all entries for a transaction.
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesByTransactionId(Long transactionId) {
        return entryRepository.findByTransactionId(transactionId);
    }

    /**
     * Gets or creates ledger account.
     */
    private LedgerAccount getOrCreateAccount(Long merchantId, AccountType accountType, String currency) {
        return accountRepository.findByMerchantIdAndAccountTypeAndCurrency(merchantId, accountType, currency)
            .orElseGet(() -> createAccount(merchantId, accountType, currency));
    }

    /**
     * Creates new ledger account.
     */
    private LedgerAccount createAccount(Long merchantId, AccountType accountType, String currency) {
        String accountCode = String.format("ACC_%d_%s_%s_%s", 
            merchantId, accountType.name(), currency, generateAlphanumeric(8));

        LedgerAccount account = LedgerAccount.builder()
            .merchantId(merchantId)
            .accountCode(accountCode)
            .accountType(accountType)
            .currency(currency)
            .description(String.format("%s account for merchant %d in %s", 
                accountType.name(), merchantId, currency))
            .build();

        return accountRepository.save(account);
    }

    /**
     * Creates ledger entry.
     */
    private LedgerEntry createEntry(
            LedgerTransaction transaction,
            LedgerAccount account,
            EntryType entryType,
            BigDecimal amount,
            String currency,
            String reference) {

        return LedgerEntry.builder()
            .transaction(transaction)
            .account(account)
            .entryType(entryType)
            .amount(amount)
            .currency(currency)
            .reference(reference)
            .build();
    }

    /**
     * Validates that debits equal credits.
     * 
     * Core accounting invariant: Sum(DEBIT) = Sum(CREDIT)
     */
    private void validateBalance(Long transactionId) {
        BigDecimal totalDebits = entryRepository.sumAmountByTransactionIdAndEntryType(
            transactionId, EntryType.DEBIT);
        BigDecimal totalCredits = entryRepository.sumAmountByTransactionIdAndEntryType(
            transactionId, EntryType.CREDIT);

        if (totalDebits == null) totalDebits = BigDecimal.ZERO;
        if (totalCredits == null) totalCredits = BigDecimal.ZERO;

        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("Unbalanced transaction: id={}, debits={}, credits={}", 
                transactionId, totalDebits, totalCredits);
            throw new BusinessException("UNBALANCED_TRANSACTION", "Unbalanced ledger transaction: debits != credits");
        }
    }

    /**
     * Validates amount is positive.
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amount must be positive: " + amount);
        }
    }

    /**
     * Validates currency code.
     */
    private void validateCurrency(String currency) {
        if (currency == null || currency.length() != 3) {
            throw new BusinessException("INVALID_CURRENCY", "Invalid currency code: " + currency);
        }
    }

    /**
     * Generates alphanumeric string for account codes.
     */
    private static String generateAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Records a dispute loss in the ledger.
     * 
     * Creates balanced double-entry transaction:
     * DEBIT   DISPUTE_LOSS         amount (expense account)
     * CREDIT  MERCHANT_RECEIVABLE  amount (reduces receivable)
     * 
     * Idempotent: Uses transaction_reference based on dispute_id.
     * 
     * @param disputeId Dispute identifier
     * @param paymentId Payment identifier
     * @param merchantId Merchant identifier
     * @param amount Dispute amount
     * @param currency Dispute currency
     * @return Created ledger transaction
     */
    @Transactional
    public LedgerTransaction recordDisputeLoss(
            String disputeId,
            String paymentId,
            Long merchantId,
            BigDecimal amount,
            String currency) {

        validateAmount(amount);
        validateCurrency(currency);

        String transactionReference = "DISPUTE_LOSS_" + disputeId;

        // Idempotency check
        if (transactionRepository.existsByTransactionReference(transactionReference)) {
            log.info("Ledger transaction already exists: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("CONCURRENT_CREATION", "Concurrent ledger creation race"));
        }

        try {
            // Get or create accounts
            LedgerAccount merchantReceivableAccount = getOrCreateAccount(
                merchantId, AccountType.MERCHANT_RECEIVABLE, currency);
            LedgerAccount disputeLossAccount = getOrCreateAccount(
                merchantId, AccountType.DISPUTE_LOSS, currency);

            // Create transaction
            LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionReference(transactionReference)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .transactionType("DISPUTE_LOSS")
                .currency(currency)
                .amount(amount)
                .description("Dispute lost: " + disputeId)
                .build();

            transaction = transactionRepository.save(transaction);

            // Create entries
            List<LedgerEntry> entries = new ArrayList<>();

            entries.add(createEntry(transaction, disputeLossAccount,
                EntryType.DEBIT, amount, currency, "Dispute loss expense"));

            entries.add(createEntry(transaction, merchantReceivableAccount,
                EntryType.CREDIT, amount, currency, "Reduce merchant receivable"));

            entries = entryRepository.saveAll(entries);

            // Validate balance
            validateBalance(transaction.getId());

            log.info("Dispute loss ledger transaction created: reference={}, amount={} {}", 
                transactionReference, amount, currency);

            return transaction;

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate dispute loss ledger transaction detected: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("LEDGER_CREATION_FAILED", "Failed to create ledger transaction"));
        }
    }

    /**
     * Records a dispute win reversal (no financial impact).
     * For audit trail only - funds were never deducted.
     * 
     * @param disputeId Dispute identifier
     * @param paymentId Payment identifier
     * @param merchantId Merchant identifier
     * @param amount Dispute amount
     * @param currency Dispute currency
     * @return Created ledger transaction
     */
    @Transactional
    public LedgerTransaction recordDisputeWin(
            String disputeId,
            String paymentId,
            Long merchantId,
            BigDecimal amount,
            String currency) {

        validateAmount(amount);
        validateCurrency(currency);

        String transactionReference = "DISPUTE_WIN_" + disputeId;

        // Idempotency check
        if (transactionRepository.existsByTransactionReference(transactionReference)) {
            log.info("Ledger transaction already exists: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("CONCURRENT_CREATION", "Concurrent ledger creation race"));
        }

        try {
            // Dispute win creates no entries (audit trail only)
            // Original CAPTURED transaction remains valid
            LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionReference(transactionReference)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .transactionType("DISPUTE_WIN")
                .currency(currency)
                .amount(amount)
                .description("Dispute won (no financial impact): " + disputeId)
                .build();

            transaction = transactionRepository.save(transaction);

            log.info("Dispute win audit record created: reference={}, amount={} {}", 
                transactionReference, amount, currency);

            return transaction;

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate dispute win record detected: reference={}", transactionReference);
            return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException("LEDGER_CREATION_FAILED", "Failed to create ledger transaction"));
        }
    }
}
