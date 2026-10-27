# PayFlow Frontend Quick Start

## Prerequisites
- Node.js 24.x installed
- PayFlow backend ready
- Development merchant seeded in database

## 5-Minute Setup

### 1. Install Dependencies
```bash
cd c:\Users\User\Desktop\PayFlow\frontend
npm install
```

### 2. Start Backend (Terminal 1)
```bash
cd c:\Users\User\Desktop\PayFlow
mvn spring-boot:run
```
Wait for: `Started PayFlowApplication`

### 3. Start Frontend (Terminal 2)
```bash
cd c:\Users\User\Desktop\PayFlow\frontend
npm start
```
Wait for: `Compiled successfully`

### 4. Configure Merchant
1. Open browser: `http://localhost:4200`
2. Click **Settings** in sidebar
3. Enter credentials:
   - **Merchant ID**: `1`
   - **API Key**: `dev_merchant_key_12345`
4. Click **Save Configuration**

### 5. View Ledger
- Automatically redirected to Ledger Explorer
- View paginated transactions
- Click ▶ to expand entries
- Verify balance checks (DR = CR)

## URLs
- Frontend: `http://localhost:4200`
- Backend API: `http://localhost:8080/api/v1`
- Swagger Docs: `http://localhost:8080/swagger-ui.html`

## Development Workflow

### Backend Changes
```bash
# Backend auto-reloads with spring-boot-devtools
# Just save Java files
```

### Frontend Changes
```bash
# Frontend auto-recompiles
# Just save TypeScript/CSS files
# Browser auto-refreshes
```

### Test Backend API
```bash
# With configured credentials
curl -H "X-API-Key: dev_merchant_key_12345" \
  http://localhost:8080/api/v1/ledger/merchants/1?page=0&size=5
```

## Troubleshooting

### "Configuration Required" in header
→ Go to Settings and enter credentials

### "Cannot connect to backend"
→ Ensure backend is running on port 8080
→ Check: `http://localhost:8080/actuator/health`

### "Authentication failed"
→ Check API key is correct: `dev_merchant_key_12345`
→ Ensure development merchant is seeded in database

### Proxy errors in console
→ Restart frontend: `npm start`
→ Ensure backend started before frontend

### Port 4200 already in use
```bash
# Kill existing process
npx kill-port 4200
npm start
```

## Available Pages

- ✅ **Ledger** - Functional (double-entry transactions)
- ✅ **Settings** - Functional (merchant config)
- 🚧 **Dashboard** - Placeholder (coming soon)
- 🚧 **Payments** - Placeholder (coming soon)
- 🚧 **Refunds** - Placeholder (coming soon)
- 🚧 **Disputes** - Placeholder (coming soon)
- 🚧 **Reconciliation** - Placeholder (coming soon)

## Next Steps

1. Explore Ledger Explorer functionality
2. Test pagination (Previous/Next buttons)
3. Expand transaction rows to view entries
4. Check balance verification indicators
5. Review developer console for HTTP requests
6. Check Network tab to see API calls with headers

## Development Tips

- **Credentials persist** across page reloads (sessionStorage)
- **Clear config** in Settings to test unconfigured state
- **Mock Gateway** indicator shows backend uses fake reconciliation
- **Correlation IDs** in console help debug API issues
- **Balance checks** verify DR = CR for each transaction
