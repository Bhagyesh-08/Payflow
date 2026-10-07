# PayFlow Ledger - Immutable Double-Entry Accounting

## Overview

PayFlow implements an immutable double-entry accounting ledger for all financial transactions. The ledger provides an auditable, append-only record of money movements through the payment system.

## Core Accounting Principle

**Double-Entry Bookkeeping**: Every financial transaction has two sides:

```
For every transaction:
Total DEBITS = Total CREDITS
```

This invariant is enforced at the database level and validated by the application.

## Domain Model

### LedgerAccount

Represents an account participating in the ledger system.

```java
- id: Unique identifier
- merchantId: Merchant owning the account (null for platform accounts)
- accountCode: Human-readable unique code (e.g., ACC_1_CUSTOMER_PAYMENT_INR_ABC123)
- accountType: Type of account (CUSTOMER_PAYMENT, MERCHANT_RECEIVABLE, etc.)
- currency: Three-letter currency code (ISO 4217)
- description: Human-readable description
```

**Account Types**:
- `CUSTOMER_PAYMENT`: Tracks money received from customers
- `MERCHANT_RECEIVABLE`: Tracks money owed to merchants
- `PLATFORM_FEE`: For platform fees (future)
- `GATEWAY_CLEARING`: For gateway reconciliation (future)
- `REFUND_PAYABLE`: For refund processing (future)

**Account Lifecycle**:
- Accounts are created automatically when needed
- One account per (merchantId, accountType, currency) combination
- Accounts are long-lived and reusable across transactions

### LedgerTransaction

Represents one atomic accounting transaction.

```java
- id: Unique identifier
- transactionReference: Business key (e.g., PAYMENT_CAPTURE_PAY_ABC123)
- paymentId: Related payment identifier
- merchantId: Merchant identifier (for isolation/filtering)
- transactionType: Type of transaction (PAYMENT_CAPTURE, REFUND, etc.)
- currency: Transaction currency
- amount: Transaction amount
- description: Human-readable description
```

**Transaction Properties**:
- **Immutable**: Never updated or deleted after creation
- **Atomic**: All entries created together or not at all
- **Balanced**: Sum(debits) = Sum(credits)
- **Idempotent**: Duplicate creation attempts return existing transaction

### LedgerEntry

Represents one side (DEBIT or CREDIT) of a transaction.

```java
- id: Unique identifier
- transactionId: Parent transaction
- accountId: Account being debited/credited
- entryType: DEBIT or CREDIT
- amount: Entry amount (always positive)
- currency: Entry currency
- reference: Additional context
```

**Entry Properties**:
- **Immutable**: Never updated or deleted
- **Positive amounts**: Negative values not allowed
- **Currency match**: All entries in a transaction have same currency

## Payment Capture Flow

When a payment is captured, the following ledger transaction is created:

```
Payment ID: PAY_ABC123
Amount: ₹1,000.00
Merchant ID: 1

Ledger Transaction:
  Reference: PAYMENT_CAPTURE_PAY_ABC123
  Type: PAYMENT_CAPTURE
  Amount: ₹1,000.00

  Entries:
    DEBIT   CUSTOMER_PAYMENT (Merchant 1, INR)     ₹1,000.00
    CREDIT  MERCHANT_RECEIVABLE (Merchant 1, INR)  ₹1,000.00

  Balance Check: ₹1,000.00 (debits) = ₹1,000.00 (credits) ✓
```

**Interpretation**:
- DEBIT CUSTOMER_PAYMENT: Money received from customer
- CREDIT MERCHANT_RECEIVABLE: Amount owed to merchant

## Event-Driven Integration

### Architecture

```
Payment Captured
      ↓
PaymentService publishes event
      ↓
Outbox Table (transactional)
      ↓
Kafka Topic: payflow.payment.events
      ↓
LedgerEventConsumer
      ↓
LedgerService.recordPaymentCapture()
      ↓
Ledger Transaction + Entries (atomic)
```

### Idempotency

**Problem**: Kafka delivers events at-least-once. Duplicate events could create duplicate ledger transactions.

**Solution**: Multi-layer idempotency:

1. **Transaction Reference Uniqueness**:
   ```sql
   transaction_reference VARCHAR(200) UNIQUE
   ```
   Reference format: `PAYMENT_CAPTURE_{paymentId}`
   
2. **Processed Event Tracking**:
   ```sql
   UNIQUE (event_id, consumer_name)
   ```
   Consumer: `LedgerEventConsumer`

3. **Application Check**:
   ```java
   if (transactionRepository.existsByTransactionReference(ref)) {
       return existingTransaction;
   }
   ```

**Result**: Even if the same PAYMENT_CAPTURED event is delivered 10 times, only ONE ledger transaction is created.

### Concurrency Safety

**Scenario**: Two consumer instances receive the same event simultaneously.

**Protection**:
1. Both check `existsByTransactionReference` → false
2. Both attempt to insert ledger transaction
3. Database unique constraint prevents duplicate
4. First wins, second gets `DataIntegrityViolationException`
5. Second reads and returns existing transaction

**Transaction Boundary**:
```java
@Transactional
public void consume(PaymentEvent event) {
    // Check processed
    // Create ledger transaction
    // Mark as processed
    // Commit atomically
}
```

## Database-Enforced Immutability

### PostgreSQL Triggers

Ledger immutability enforced at database level via triggers:

```sql
-- Prevents UPDATE operations
CREATE TRIGGER prevent_ledger_transaction_update
    BEFORE UPDATE ON ledger_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_transaction_modification();

-- Prevents DELETE operations
CREATE TRIGGER prevent_ledger_transaction_delete
    BEFORE DELETE ON ledger_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_transaction_modification();
```

**Effect**:
- Any UPDATE attempt raises exception: "Ledger transactions are immutable"
- Any DELETE attempt raises exception: "Ledger transactions are immutable"
- INSERT operations work normally
- Applies to both `ledger_transactions` and `ledger_entries` tables

### Why Database-Level Enforcement?

**Defense in Depth**:
- Application prevents modifications (no UPDATE/DELETE in code)
- Database prevents modifications (triggers block direct SQL)
- Even admin SQL commands cannot modify ledger
- Protects against bugs, accidents, and unauthorized access

**Correction Mechanism**:
```
Wrong ledger entry? → Create compensating transaction
NOT: UPDATE original record
```

## Immutability

### What is Immutable?

- `LedgerTransaction` records
- `LedgerEntry` records
- `LedgerAccount` records (once created)

### Why Immutable?

1. **Audit Trail**: Complete history preserved
2. **Data Integrity**: No accidental modifications
3. **Compliance**: Regulatory requirements
4. **Debugging**: Can replay history
5. **Concurrency**: No update conflicts

### How is Immutability Enforced?

**Application Layer**:
- No UPDATE/DELETE methods in service
- No mutation endpoints in controller (only GET)

**API Layer**:
```java
// Allowed
GET /api/v1/ledger/payments/{paymentId}
GET /api/v1/ledger/merchants/{merchantId}

// NOT provided
PUT /api/v1/ledger/transactions/{id}
PATCH /api/v1/ledger/transactions/{id}
DELETE /api/v1/ledger/transactions/{id}
```

**Database Layer**:
- No application triggers for UPDATE/DELETE
- Row-level security could be added (future)

### Correcting Errors

If a ledger entry is incorrect, create a **compensating transaction**:

```
Original (incorrect):
DEBIT  A  ₹100
CREDIT B  ₹100

Reversal:
DEBIT  B  ₹100
CREDIT A  ₹100
```

The original transaction remains in the ledger. The correction is a new transaction.

## Database Schema

### Constraints

**ledger_transactions**:
```sql
amount > 0                           -- Positive amounts only
transaction_reference UNIQUE         -- Idempotency key
```

**ledger_entries**:
```sql
amount > 0                           -- Positive amounts only
entry_type IN ('DEBIT', 'CREDIT')   -- Valid types only
FOREIGN KEY (transaction_id)         -- Referential integrity
FOREIGN KEY (account_id)            -- Referential integrity
```

**ledger_accounts**:
```sql
account_code UNIQUE                                    -- Globally unique
UNIQUE (merchant_id, account_type, currency)          -- One account per combination
```

### Indexes

**Performance**:
```sql
idx_ledger_transactions_payment    (payment_id)
idx_ledger_transactions_merchant   (merchant_id)
idx_ledger_entries_transaction     (transaction_id)
idx_ledger_entries_account         (account_id)
```

## Validation Rules

### Amount Validation

```java
✓ BigDecimal("1000.00")     -- Valid positive amount
✗ BigDecimal("0")           -- Zero not allowed
✗ BigDecimal("-100")        -- Negative not allowed
✗ null                      -- Null not allowed
```

### Currency Validation

```java
✓ "INR", "USD", "EUR"       -- Valid 3-letter codes
✗ "DOLLAR"                  -- Too long
✗ "IN"                      -- Too short
✗ null                      -- Null not allowed
```

### Balance Validation

```java
// After creating all entries
BigDecimal totalDebits = sumDebits(transactionId);
BigDecimal totalCredits = sumCredits(transactionId);

if (totalDebits.compareTo(totalCredits) != 0) {
    throw new BusinessException("Unbalanced transaction");
}
```

This validation runs **before** transaction commit. Unbalanced transactions are never persisted.

### Currency Consistency

All entries in a transaction must have the same currency:

```java
✓ Transaction: INR
  Entry 1: DEBIT  A  ₹1000 INR
  Entry 2: CREDIT B  ₹1000 INR

✗ Transaction: INR  
  Entry 1: DEBIT  A  $1000 USD  ← REJECTED
  Entry 2: CREDIT B  ₹1000 INR
```

**Future**: Multi-currency transactions with FX conversion will require explicit design.

## Merchant Isolation

### Principle

Merchant A cannot access Merchant B's ledger data.

### Implementation

**Service Layer**:
```java
List<LedgerTransaction> getByMerchantId(Long merchantId) {
    // Only returns transactions for specified merchant
}
```

**Controller Layer**:
```java
@GetMapping("/merchants/{merchantId}")
public ResponseEntity<List<LedgerTransactionResponse>> getLedger(
    @PathVariable Long merchantId,
    @AuthenticatedMerchant Long authenticatedMerchantId) {
    
    if (!merchantId.equals(authenticatedMerchantId)) {
        throw new UnauthorizedException();
    }
    // ... proceed
}
```

**Note**: Phase 5A does NOT implement authentication. Merchant isolation is enforced at the data model level, but API authentication is planned for future phases.

## REST API

### GET /api/v1/ledger/payments/{paymentId}

Retrieves ledger transaction for a payment.

**Response**:
```json
{
  "id": 1,
  "transactionReference": "PAYMENT_CAPTURE_PAY_ABC123",
  "paymentId": "PAY_ABC123",
  "merchantId": 1,
  "transactionType": "PAYMENT_CAPTURE",
  "currency": "INR",
  "amount": 1000.00,
  "description": "Payment captured: PAY_ABC123",
  "createdAt": "2026-09-29T10:00:00",
  "entries": [
    {
      "id": 1,
      "accountCode": "ACC_1_CUSTOMER_PAYMENT_INR_XYZ",
      "accountType": "CUSTOMER_PAYMENT",
      "entryType": "DEBIT",
      "amount": 1000.00,
      "currency": "INR",
      "reference": "Payment from customer"
    },
    {
      "id": 2,
      "accountCode": "ACC_1_MERCHANT_RECEIVABLE_INR_ABC",
      "accountType": "MERCHANT_RECEIVABLE",
      "entryType": "CREDIT",
      "amount": 1000.00,
      "currency": "INR",
      "reference": "Amount receivable by merchant"
    }
  ]
}
```

### GET /api/v1/ledger/merchants/{merchantId}

Retrieves all ledger transactions for a merchant.

**Response**: Array of transaction responses (same structure as above).

## Failure Handling

### Event Processing Failures

**Malformed Event**:
- Log error with full context
- Do NOT acknowledge to Kafka
- Kafka will redeliver
- After max retries → stays in topic (manual intervention)

**Missing Payment**:
- Log warning
- Acknowledge event (not retriable)
- Event appears in processed_events
- Payment may arrive later (out-of-order)

**Duplicate Event**:
- Idempotency check detects
- Log "already processed"
- Acknowledge immediately
- No ledger modification

**Database Constraint Violation**:
- Catch `DataIntegrityViolationException`
- Read existing transaction
- Return existing transaction
- Acknowledge event

### Unbalanced Transaction

```java
throw new BusinessException("UNBALANCED_TRANSACTION", 
    "Unbalanced ledger transaction: debits != credits");
```

This is a **programming error**, not a runtime error. If this occurs:
1. Review LedgerService logic
2. Check entry creation
3. Verify amount calculations

It should never occur in production if tests pass.

## Testing Strategy

### Unit Tests

**LedgerServiceTest** (8 tests):
- Balanced transaction creation
- Zero amount rejection
- Negative amount rejection
- Null amount rejection
- Invalid currency rejection
- Idempotent duplicate handling
- Payment lookup (found/not found)

**LedgerEventConsumerTest** (4 tests):
- PAYMENT_CAPTURED creates ledger transaction
- Non-capture events skipped
- Already-processed events skipped
- Duplicate constraint violation handled

### Integration Tests

Blocked by Docker/Testcontainers connectivity issue (Windows named pipe).

**Planned Tests**:
- End-to-end: Payment capture → Event → Ledger transaction
- Concurrent duplicate event handling
- Database constraint verification

### Invariant Testing

All tests verify:
```java
assertThat(sumDebits).isEqualTo(sumCredits);
```

This assertion appears throughout the test suite to make the accounting invariant visible.

## Known Limitations

### What is NOT Implemented

**Phase 5A Scope Boundaries**:
- ❌ Multi-currency transactions (FX conversion)
- ❌ Fee/commission accounting
- ❌ Settlement processing
- ❌ Refund accounting
- ❌ Gateway reconciliation
- ❌ Account balance snapshots
- ❌ Financial reports/dashboards
- ❌ API authentication/authorization
- ❌ Audit log export
- ❌ Compensating transaction helper

**Future Phases**:
- Phase 5B: Gateway reconciliation
- Phase 6: Refund accounting
- Phase 7: Settlement processing
- Phase 8: Financial reporting

### Currency Support

**Current**: Single currency per transaction
**Future**: Multi-currency with explicit FX rates

### Account Types

**Current**: Minimal set for payment capture flow
**Future**: Expand as new flows are added (refunds, fees, etc.)

### Performance

**Current**: No optimization for high-volume merchants
**Future Considerations**:
- Partitioning by merchant_id or date
- Read replicas for reporting queries
- Materialized account balances

## Observability

### Structured Logging

All ledger operations log with context:
```java
log.info("Ledger transaction created: reference={}, amount={} {}", 
    transactionReference, amount, currency);

log.warn("Duplicate ledger transaction detected: reference={}", 
    transactionReference);

log.error("Unbalanced transaction: id={}, debits={}, credits={}", 
    transactionId, totalDebits, totalCredits);
```

### Metrics (Future)

- Ledger transactions created per minute
- Average transaction amount
- Unbalanced transaction attempts (should be zero)
- Duplicate event rate

## Compliance Considerations

**What This Provides**:
- Immutable audit trail
- Double-entry accounting
- Transaction atomicity
- Data integrity constraints

**What This Does NOT Provide**:
- PCI-DSS compliance
- SOC 2 certification
- Banking-grade reconciliation
- Regulatory reporting

PayFlow is a demonstration project. Production use requires:
- Independent security audit
- Compliance certification
- Regulatory approval
- Penetration testing

## References

- [Double-Entry Bookkeeping](https://en.wikipedia.org/wiki/Double-entry_bookkeeping)
- [Accounting Equation](https://en.wikipedia.org/wiki/Accounting_equation)
- [Immutable Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- PayFlow Architecture: `ARCHITECTURE.md`
- PayFlow Events: `EVENTS.md`
