# PayFlow Frontend

Angular 19 frontend for PayFlow payment processing and reconciliation platform.

## Architecture

- **Framework**: Angular 19.2.0 with standalone components
- **Styling**: Vanilla CSS with CSS custom properties (design system)
- **HTTP**: Angular HttpClient with interceptors
- **Routing**: Angular Router
- **State**: Signals for reactive state management

## Project Structure

```
src/app/
├── core/
│   ├── config/          # API configuration
│   ├── interceptors/    # HTTP interceptors (API key, correlation ID)
│   ├── models/          # TypeScript interfaces matching backend DTOs
│   └── services/        # API services
├── features/
│   ├── dashboard/       # Overview dashboard with widgets
│   ├── payments/        # Payments management with lifecycle actions
│   ├── refunds/         # Refunds workflow (via payments)
│   ├── disputes/        # Disputes with evidence submission
│   ├── reconciliation/  # Reconciliation runs and discrepancies
│   ├── ledger/          # Ledger Explorer
│   └── settings/        # Merchant configuration
└── shared/
    └── components/      # Reusable UI components
```

## Prerequisites

- Node.js 24.x
- npm 11.x
- Running PayFlow backend at `http://localhost:8080`

## Installation

```bash
cd frontend
npm install
```

## Development

### Start Complete Application

1. **Start backend**:
   ```bash
   mvn spring-boot:run
   ```

2. **Start frontend**:
   ```bash
   cd frontend
   npm start
   ```

3. Open `http://localhost:4200`

4. Configure credentials in Settings:
   - Merchant ID: `1`
   - API Key: `dev_merchant_key_12345`

### Initial Setup

Apply development merchant seed (if not done):
```bash
psql -U postgres -d payflow
\i src/main/resources/db/dev-data/V999__dev_merchant_seed.sql
```

## Available Scripts

- `npm start` - Dev server with proxy (port 4200)
- `npm run build` - Production build
- `npm test` - Run unit tests (32 tests)
- `npm run watch` - Watch mode build

## Application Routes

- `/dashboard` - Overview with recent activity widgets
- `/payments` - Payment search, details, lifecycle actions, refunds
- `/refunds` - Redirects to payments page
- `/disputes` - Dispute list with evidence submission
- `/reconciliation` - Runs and discrepancies with pagination
- `/ledger` - Immutable ledger explorer
- `/settings` - Merchant credential configuration

## Features

### Dashboard
- Quick action links
- Recent ledger entries (5 most recent)
- Dispute status summary
- Latest reconciliation run metrics
- System notices for simulation modes

### Payments
- Search by database ID or payment ID
- Payment detail view with all fields
- Lifecycle actions: Initiate → Authorize → Capture
- State-dependent action buttons
- Refund creation with validation
- Refunds list per payment
- Paginated payments table

### Disputes
- Merchant disputes table
- Expandable detail rows
- Evidence submission form (OPEN status only)
- Mock gateway simulation warning
- Status badges

### Reconciliation
- Tab view: Runs vs Discrepancies
- Start reconciliation form
- Paginated tables
- Run metrics (total, matched, discrepancies)
- Synthetic data warning

### Ledger
- Paginated transaction list
- Expandable rows with debit/credit entries
- Balance verification per transaction
- Merchant-scoped data

## Authentication

API key authentication via HTTP interceptors:

1. **ConfigService**: Stores credentials (sessionStorage)
2. **apiKeyInterceptor**: Attaches `X-API-Key` header
3. **correlationIdInterceptor**: Generates request tracking UUIDs

**Security Notes:**
- Development convenience only
- SessionStorage not secure against XSS
- Production needs proper auth flow
- No keys in source control

## Backend Integration

### Endpoints Used

**Ledger:**
- `GET /api/v1/ledger/merchants/{merchantId}`
- `GET /api/v1/ledger/payments/{paymentId}`

**Payments:**
- `GET /api/v1/payments` (paginated)
- `GET /api/v1/payments/{id}`
- `GET /api/v1/payments/payment-id/{paymentId}`
- `POST /api/v1/payments/{id}/initiate`
- `POST /api/v1/payments/{id}/authorize`
- `POST /api/v1/payments/{id}/capture`

**Refunds:**
- `POST /api/v1/payments/{paymentId}/refunds`
- `GET /api/v1/payments/{paymentId}/refunds`
- `GET /api/v1/refunds/{refundId}`

**Disputes:**
- `GET /api/v1/merchants/{merchantId}/disputes`
- `GET /api/v1/disputes/{disputeId}`
- `POST /api/v1/disputes/{disputeId}/evidence`

**Reconciliation:**
- `POST /api/v1/reconciliation/runs`
- `GET /api/v1/reconciliation/runs/{id}`
- `GET /api/v1/reconciliation/merchants/{merchantId}/runs`
- `GET /api/v1/reconciliation/merchants/{merchantId}/discrepancies`

## Simulation Warnings

**Disputes:** Mock gateway simulation mode clearly indicated
**Reconciliation:** Synthetic provider records warning displayed

Both warnings remain visible throughout the UI to prevent confusion with real gateway data.

## Testing

**Test Count:** 32 tests passing

Coverage:
- ConfigService (6 tests)
- LedgerService (2 tests)
- PaymentService (6 tests)
- RefundService (4 tests)
- DisputeService (5 tests)
- ReconciliationService (5 tests)
- Component creation tests (4 tests)

Run tests:
```bash
npm test
```

## Build

Production build:
```bash
npm run build
```

**Build Output:** 442.33 kB (106.63 kB gzipped)

CSS warnings for components exceeding 4 KB budget (all under 8 KB hard limit).

## Known Limitations

1. SessionStorage credential persistence not encrypted
2. No refresh token or session expiry handling
3. Disputes and reconciliation use mock/synthetic data
4. No file upload for dispute evidence (text only)
5. Financial KPIs not displayed (no aggregation endpoints)
6. Balance verification uses floating-point arithmetic

## Production Readiness Limitations

- **NOT PCI-DSS compliant** (dev API key auth)
- **NOT production-ready** (mock gateways)
- **Portfolio demonstration only**
- Requires proper authentication flow for production
- Requires real payment gateway integration
- Requires proper secret management

## Design System

Professional fintech interface:

- **Dark theme**: Navy/slate (#0f172a, #1e293b)
- **Accents**: Blue (#3b82f6), teal (#06b6d4)
- **Typography**: System fonts, clear hierarchy
- **Responsive**: Desktop-first, tablet/mobile compatible
- **Accessible**: High contrast, keyboard navigation

See `src/styles.css` for CSS custom properties.
