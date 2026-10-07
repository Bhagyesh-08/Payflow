# PayFlow Gateway Reconciliation

## Overview

Reconciliation compares internal payment records against gateway payment records to detect discrepancies. It does NOT modify historical ledger entries.

## Core Principle

**Detection, Not Correction**: Reconciliation identifies mismatches but preserves immutable financial history.

## Domain Model

### ReconciliationRun
Audit trail of reconciliation execution.

**Fields**: merchantId, gateway, businessDate, status, totalRecords, matchedRecords, discrepancyRecords

**Idempotency**: Unique (merchantId, gateway, businessDate)

### ReconciliationRecord
Result of comparing one gateway record vs internal record.

**Fields**: gatewayReference, paymentId, amounts, statuses, result, discrepancyType

**Results**: MATCHED, DISCREPANCY, UNRESOLVED

## Matching Rules

**Rule A - Exact Match**:
```
gatewayPaymentId matches internal gatewayPaymentId
AND amount matches
AND currency matches
AND status compatible
→ MATCHED
```

**Rule B - Amount Mismatch**:
```
Internal: ₹1,000
Gateway:  ₹950
→ DISCREPANCY (AMOUNT_MISMATCH)
```

**Rule C - Currency Mismatch**:
```
Internal: INR
Gateway:  USD
→ DISCREPANCY (CURRENCY_MISMATCH)
```

**Rule D - Missing Internal Payment**:
```
Gateway has: pay_999
Internal: no matching payment
→ DISCREPANCY (MISSING_INTERNAL_PAYMENT)
```

**Rule F - Status Mismatch**:
```
Internal: CAPTURED
Gateway:  FAILED
→ DISCREPANCY (STATUS_MISMATCH)
```

## Architecture

```
ReconciliationService
  ↓
ReconciliationDataProvider (abstraction)
  ↓
FakeRazorpayReconciliationProvider
```

**Gateway Abstraction**: Keeps reconciliation logic independent of specific gateway.

## Razorpay Integration

**Limitation**: Razorpay SDK 1.4.6 does not expose dedicated reconciliation/settlement API suitable for this implementation.

**Solution**: FakeRazorpayReconciliationProvider returns deterministic test data.

**Production**: Would integrate with Razorpay's actual reconciliation/settlement API when available.

## Flow

```
POST /api/v1/reconciliation/runs
  ↓
ReconciliationService.startReconciliationRun()
  ↓
Fetch gateway records (via provider)
  ↓
For each gateway record:
  - Find internal payment by gatewayPaymentId
  - Compare amount, currency, status
  - Classify as MATCHED/DISCREPANCY
  ↓
Save ReconciliationRecords (atomic)
  ↓
Calculate summary (matched, discrepancies)
  ↓
Return ReconciliationRun
```

## Idempotency

**Database Constraint**: UNIQUE (merchantId, gateway, businessDate)

**Behavior**: Duplicate reconciliation request returns existing run without reprocessing.

## Ledger Relationship

Reconciliation references payments which link to ledger transactions.

**Critical**: Reconciliation NEVER modifies ledger entries. Discrepancies are recorded, not automatically corrected.

## API Endpoints

**POST /api/v1/reconciliation/runs**: Start reconciliation

**GET /api/v1/reconciliation/runs/{id}**: Get run details

**GET /api/v1/reconciliation/runs/{id}/records**: Get records for run

**GET /api/v1/reconciliation/merchants/{merchantId}/runs**: Get merchant's runs

**GET /api/v1/reconciliation/merchants/{merchantId}/discrepancies**: Get discrepancies

## Database Schema

**reconciliation_runs**: Run metadata, summary counts

**reconciliation_records**: Individual comparison results (immutable)

**Constraints**:
- UK: (merchantId, gateway, businessDate)
- UK: (reconciliation_run_id, gateway, gateway_reference)

## Known Limitations

- Razorpay SDK 1.4.6 lacks reconciliation API
- Using fake provider for demonstration
- Settlement reconciliation not implemented
- No automated correction workflow
- No multi-currency reconciliation
- No fee/commission reconciliation
