# PayFlow - Quick Start Guide

## Prerequisites
- Java 21
- Maven 3.9+
- Docker & Docker Compose

## Start in 3 Steps

### 1. Start Database
```bash
docker compose up -d
```

### 2. Run Application
```bash
mvn spring-boot:run
```

### 3. Test API
```bash
curl http://localhost:8080/actuator/health
```

## Quick API Test

### PowerShell
```powershell
# Create Merchant
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/merchants" `
  -Method Post -ContentType "application/json" `
  -Body '{"businessName":"MyStore","email":"store@example.com","phone":"1234567890"}'

# Create Order (use merchantId from above)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" `
  -Method Post -ContentType "application/json" `
  -Body '{"merchantId":1,"amount":1000,"currency":"INR","customerEmail":"buyer@example.com"}'

# Create Payment (use orderId from above)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" `
  -Method Post -ContentType "application/json" `
  -Body '{"orderId":1,"paymentMethod":"UPI"}'

# Get Payment
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/1" -Method Get
```

### Bash/Linux
```bash
# Create Merchant
curl -X POST http://localhost:8080/api/v1/merchants \
  -H "Content-Type: application/json" \
  -d '{"businessName":"MyStore","email":"store@example.com","phone":"1234567890"}'

# Create Order
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"merchantId":1,"amount":1000,"currency":"INR","customerEmail":"buyer@example.com"}'

# Create Payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"paymentMethod":"UPI"}'

# Get Payment
curl http://localhost:8080/api/v1/payments/1
```

## Running Tests

```bash
# Unit tests only (no Docker needed)
mvn test -Dtest=*ServiceTest

# All tests (requires Docker)
mvn test
```

## Stopping

```bash
# Stop application: Ctrl+C

# Stop database
docker compose down
```

## Key URLs
- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Database: localhost:5433 (postgres/payflow/payflow_password)

## Troubleshooting

**Port 5433 in use?**
Edit `docker-compose.yml` and change port mapping.

**Port 8080 in use?**
Add to `application.yml`:
```yaml
server:
  port: 8081
```

**Build fails?**
```bash
mvn clean install -U
```

**Database connection issues?**
```bash
docker compose logs postgres
```

## Next Steps
- Read [README.md](./README.md) for detailed documentation
- Read [ARCHITECTURE.md](./ARCHITECTURE.md) for design decisions
- Read [PHASE1_VERIFIED.md](./PHASE1_VERIFIED.md) for verification report
