# PayFlow - Payment Processing & Reconciliation Platform

Enterprise payment gateway integration system with **Java 21**, **Spring Boot 3**, and **Angular 19** frontend, demonstrating full-stack financial transaction management, event-driven architecture, and operational observability.

## 🎯 Project Overview

PayFlow is a comprehensive payment processing platform handling the complete payment lifecycle: payments, refunds, disputes, and financial reconciliation. Built as a **portfolio demonstration project**, it showcases professional backend architecture patterns, financial domain modeling, and modern frontend development.

**Purpose**: Technical demonstration for software engineering interviews and code reviews. Not production-ready.

## 🏗️ Architecture

**Backend**: Modular monolith with clear domain boundaries
**Frontend**: Angular 19 SPA with standalone components
**Integration**: RESTful API with event-driven internals

### Key Architectural Patterns

- **Modular Monolith**: Clean separation across payment, refund, dispute, ledger, and reconciliation domains
- **Event-Driven**: Transactional outbox pattern with Kafka for reliable event processing
- **State Machine**: Enforced state transitions for payments, refunds, and disputes
- **Double-Entry Ledger**: Immutable accounting system with compensating transactions
- **Gateway Abstraction**: Provider-agnostic integration layer (Razorpay mock implementation)
- **Idempotency**: Multi-layer duplicate prevention (Redis + Database constraints)
- **API Key Auth**: Simple merchant-level authentication (development pattern)

## 🛠️ Technology Stack

### Backend
- **Runtime**: Java 21, Spring Boot 3.2.5
- **Database**: PostgreSQL 15 with Flyway migrations
- **Cache**: Redis (Lettuce client)
- **Messaging**: Apache Kafka
- **Security**: Spring Security with custom API key filter
- **Monitoring**: Micrometer, Prometheus, Actuator
- **Build**: Maven 3.9+
- **Testing**: JUnit 5, Mockito, Testcontainers

### Frontend
- **Framework**: Angular 19.2.0 (standalone components)
- **Language**: TypeScript 5.7
- **Styling**: Vanilla CSS with design system
- **HTTP**: Angular HttpClient with interceptors
- **State**: Signals for reactive state
- **Build**: Angular CLI with production optimization
- **Testing**: Jasmine, Karma, ChromeHeadless

### Infrastructure
- **Containers**: Docker Compose for local development
- **Orchestration**: Single-instance deployment
- **Monitoring**: Grafana + Prometheus stack

## 📁 Project Structure

```
PayFlow/
├── src/main/java/com/payflow/      # Backend Java source
│   ├── common/                      # Shared utilities, security
│   ├── payment/                     # Payment domain
│   ├── refund/                      # Refund domain
│   ├── dispute/                     # Dispute management
│   ├── ledger/                      # Double-entry accounting
│   ├── reconciliation/              # Financial reconciliation
│   ├── event/                       # Outbox, Kafka consumers
│   ├── webhook/                     # Webhook processing
│   ├── infrastructure/              # Gateway integrations
│   └── merchant/                    # Merchant management
├── frontend/                        # Angular application
│   ├── src/app/core/               # Services, models, interceptors
│   ├── src/app/features/           # Feature modules
│   └── src/app/shared/             # Reusable components
├── docker-compose.yml              # Local infrastructure
└── ARCHITECTURE.md                 # Detailed design docs
```

## ✨ Key Features

### Payment Processing
- Complete lifecycle: CREATED → INITIATED → AUTHORIZED → CAPTURED/FAILED
- Idempotent creation via Redis + database constraints
- Optimistic locking for concurrent updates
- Gateway abstraction supporting multiple providers

### Refund Management
- Partial and full refund support
- Over-refund prevention with captured amount validation
- Idempotency keys for safe retries
- State machine: REQUESTED → PROCESSING → SUCCEEDED/FAILED
- Ledger integration for financial tracking

### Dispute Handling
- Lifecycle: OPEN → EVIDENCE_SUBMITTED → WON/LOST/CLOSED
- Evidence submission workflow
- Mock gateway simulation (clearly labeled)
- Automated ledger adjustments for outcomes

### Financial Ledger
- Immutable double-entry accounting
- PostgreSQL triggers enforce immutability
- Compensating transactions for refunds/disputes
- Balance verification (debits = credits)

### Reconciliation
- Automated matching: internal records vs gateway data
- Discrepancy detection and reporting
- Idempotent runs with synthetic test data
- Clearly labeled as simulation mode

### Event-Driven Architecture
- Transactional outbox for reliable publishing
- Idempotent Kafka consumers with event tracking
- At-least-once delivery with duplicate detection
- Correlation IDs for request tracing

### Frontend (Angular 19)
- **Dashboard**: Activity widgets with real data
- **Payments**: Search, detail, lifecycle actions, refunds
- **Disputes**: Evidence submission with mock warnings
- **Reconciliation**: Runs and discrepancies with pagination
- **Ledger**: Transaction explorer with balance verification
- **Responsive UI**: Professional fintech design

### API & Observability
- **OpenAPI/Swagger**: Interactive docs at `/swagger-ui.html`
- **Pagination**: All list endpoints support paging/sorting
- **Consistent Errors**: Standardized error responses
- **Prometheus Metrics**: Business and technical metrics
- **Health Checks**: PostgreSQL, Redis, Kafka status
- **Correlation IDs**: Request tracking across services

## 🚀 Getting Started

### Prerequisites
- Java 21
- Node.js 24.x (for frontend)
- Docker & Docker Compose
- Maven 3.9+

### Backend Setup

1. **Start infrastructure**
```bash
docker-compose up -d
```

2. **Build and run**
```bash
mvn clean install
mvn spring-boot:run
```

3. **Apply dev merchant seed** (first time)
```bash
psql -U postgres -d payflow
\i src/main/resources/db/dev-data/V999__dev_merchant_seed.sql
```

### Frontend Setup

```bash
cd frontend
npm install
npm start
```

Access at http://localhost:4200

**Initial Configuration:**
1. Navigate to Settings
2. Enter Merchant ID: `1`
3. Enter API Key: `dev_merchant_key_12345`

### Access Points
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus
- **Grafana**: http://localhost:3000 (admin/admin)

## 📊 Testing

### Backend Tests
```bash
mvn test
```

**Results**: 189/198 tests passing
- ✅ **189 passing**: All unit and service tests
- ⚠️ **9 errors**: Docker/Testcontainers (environment-specific, not code defects)
  - PayFlowApplicationTests
  - LedgerDisputeTest (7 tests)
  - PaymentConcurrencyTest

**Note**: Testcontainers failures are due to Docker unavailability, not application bugs.

### Frontend Tests
```bash
cd frontend
npm test
```

**Results**: 32/32 tests passing ✅

### Production Build
```bash
cd frontend
npm run build
```

**Bundle**: 442 kB (107 kB gzipped) ✅

## 📖 Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System design and patterns
- **[QUICK_START.md](QUICK_START.md)** - API examples and workflows
- **[OBSERVABILITY.md](OBSERVABILITY.md)** - Monitoring and operations
- **[LEDGER.md](LEDGER.md)** - Accounting system design
- **[RECONCILIATION.md](RECONCILIATION.md)** - Reconciliation process
- **[frontend/README.md](frontend/README.md)** - Frontend architecture
- **[PRODUCTION_READINESS_AUDIT.md](PRODUCTION_READINESS_AUDIT.md)** - Quality assessment

## 🔑 Configuration

Key properties (see `application.yml`):

```yaml
# Database
spring.datasource.url=jdbc:postgresql://localhost:5433/payflow

# Redis  
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Gateway (mock for development)
razorpay.key-id=${RAZORPAY_KEY_ID:test_key}
razorpay.key-secret=${RAZORPAY_KEY_SECRET:test_secret}
```

## 🎓 Learning Outcomes

This project demonstrates:

**Backend Engineering:**
- Spring Boot application architecture
- Domain-driven design with clear boundaries
- State machine pattern for business workflows
- Event-driven architecture with Kafka
- Transactional integrity and consistency
- Financial domain modeling (double-entry ledger)

**Data & Persistence:**
- PostgreSQL with advanced features (triggers, constraints)
- Flyway schema migrations
- Redis caching and idempotency
- Optimistic locking for concurrency

**Frontend Development:**
- Angular 19 with standalone components
- TypeScript type safety
- HTTP interceptors for cross-cutting concerns
- Responsive design patterns
- Professional UI/UX for financial applications

**Integration & API Design:**
- RESTful API design with pagination
- OpenAPI/Swagger documentation
- Gateway abstraction patterns
- Webhook processing and signature verification

**Testing & Quality:**
- Unit testing with JUnit and Mockito
- Frontend testing with Jasmine
- Test-driven development practices
- Integration testing approaches

**Observability:**
- Structured logging with correlation IDs
- Prometheus metrics
- Health checks and actuator endpoints

## ⚠️ Known Limitations

**Security:**
- Simple API key auth (not OAuth/JWT)
- SessionStorage in frontend (not secure)
- Not PCI-DSS compliant
- Development-only credential management

**Functionality:**
- Mock dispute gateway (not real provider)
- Synthetic reconciliation data
- No file upload capability
- Limited payment methods

**Scalability:**
- Single-instance deployment
- No load testing performed
- No distributed tracing
- No circuit breakers

**Testing:**
- 9 integration tests require Docker
- No automated browser E2E tests
- Manual verification recommended

**Production Readiness:**
- ❌ Not production-ready
- ❌ No real payment gateway integration
- ❌ No compliance certifications
- ❌ No security hardening
- ❌ No operational runbooks

## 🎯 Future Improvements

- OAuth 2.0 / JWT authentication
- Real Razorpay integration
- Additional payment gateways (Stripe, PayPal)
- Settlement file processing
- Advanced fraud detection
- Comprehensive E2E test suite
- Kubernetes deployment manifests
- Distributed tracing (Jaeger/Zipkin)

## 🤝 Contributing

This is a portfolio project. Feel free to fork and experiment!

## 📝 License

MIT License - Open source for educational purposes

## 👤 Author

**Bhagyesh Suthar**
- GitHub: [@Bhagyesh-08](https://github.com/Bhagyesh-08)

---

**Portfolio Project Disclaimer**: This application demonstrates software engineering capabilities and is not intended for production use without significant additional work including security hardening, compliance review, real gateway integration, and operational setup.
