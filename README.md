# Event Ledger

A take-home assessment demonstrating two independent Spring Boot 3 microservices with idempotent transaction handling, circuit breaker patterns, and distributed tracing.

## Architecture

### Services

**event-gateway** (port 8080)
- Public-facing API gateway
- Receives transaction events from clients
- Persists events locally with status tracking (PENDING → APPLIED / ACCOUNT_SERVICE_UNAVAILABLE)
- Forwards validated events to account-service
- Implements Circuit Breaker and Timeout patterns for account-service calls
- Generates trace IDs for request correlation

**account-service** (port 8081)
- Internal service (not client-facing)
- Stores transactions and computes account balances
- Idempotent transaction handling via transactionId
- Never stores balance — computed on-demand as SUM(CREDIT) - SUM(DEBIT)

### Key Design Decisions

**Idempotency**
- Both services implement idempotency via unique IDs (eventId / transactionId)
- event-gateway tracks submission status (APPLIED = already forwarded successfully)
- account-service deduplicates via unique constraint + DataIntegrityViolationException fallback
- Duplicate requests return 200 with `X-Idempotent-Replay: true` header

**Balance Computation**
- No balance column in database
- Computed dynamically: `SUM(CASE WHEN type='CREDIT' THEN amount ELSE -amount END)`
- Ensures consistency — balance always reflects actual transaction history

**Status Tracking**
- Events persist with status even when account-service is unavailable
- Status values: PENDING (initial), APPLIED (success), ACCOUNT_SERVICE_UNAVAILABLE (retry later)
- Re-submitting an event in PENDING/ACCOUNT_SERVICE_UNAVAILABLE state triggers retry

## Resiliency Patterns

### Implemented: Circuit Breaker + Timeout

**Circuit Breaker** (Resilience4j)
- Sliding window: 10 requests
- Failure threshold: 50%
- Open state duration: 10 seconds
- Prevents cascading failures when account-service is down
- Fast-fails after threshold reached, avoiding wasted resources

**Timeout** (Resilience4j TimeLimiter)
- 3-second timeout on account-service calls
- Prevents indefinite blocking on slow/hung downstream service
- Combined with 3s read timeout on RestClient

### Not Implemented: Bulkhead, Retry

**Bulkhead** - Not needed
- Single downstream dependency (account-service)
- No thread pool exhaustion risk from multiple slow services
- Would add complexity without benefit in this two-service architecture

**Retry** - Intentionally omitted
- Events persist with ACCOUNT_SERVICE_UNAVAILABLE status when downstream fails
- Client can re-submit the same eventId to retry (explicit retry, client-controlled)
- Automatic retries could amplify load during downstream outages
- Idempotency + status tracking gives clients full control over retry timing

## Technology Stack

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- H2 in-memory database
- Resilience4j (Circuit Breaker, TimeLimiter)
- Micrometer + Prometheus metrics
- Logstash Logback Encoder (JSON logging)
- Lombok
- JUnit 5 + MockMvc

## Setup

### Prerequisites

- Java 17+
- Maven 3.6+
- Docker & Docker Compose (for containerized setup)

### Build

```bash
# Build both services
cd account-service
mvn clean package
cd ../event-gateway
mvn clean package
```

## Running the Services

### Option 1: Docker Compose (Recommended)

```bash
# From project root
docker-compose up --build

# Services available at:
# - event-gateway: http://localhost:8080
# - account-service: http://localhost:8081 (internal only)
```

### Option 2: Manual (Local)

**Terminal 1: Start account-service**
```bash
cd account-service
mvn spring-boot:run
```

**Terminal 2: Start event-gateway**
```bash
cd event-gateway
ACCOUNT_SERVICE_URL=http://localhost:8081 mvn spring-boot:run
```

## API Endpoints

### Event Gateway (Port 8080)

**POST /events** - Submit a transaction event
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "ACC123",
    "type": "CREDIT",
    "amount": 100.0,
    "currency": "USD",
    "eventTimestamp": "2026-07-24T00:00:00Z",
    "metadata": {"source": "mobile"}
  }'
# Response: 201 (new), 200 + X-Idempotent-Replay header (duplicate), 503 (account-service unavailable)
```

**GET /events/{eventId}** - Retrieve event by ID
```bash
curl http://localhost:8080/events/evt-001
```

**GET /events?account={accountId}** - List events for account
```bash
curl http://localhost:8080/events?account=ACC123
```

**GET /accounts/{accountId}/balance** - Get current balance
```bash
curl http://localhost:8080/accounts/ACC123/balance
# Response: {"accountId":"ACC123","balance":100.0,"currency":"USD"}
```

**GET /health** - Health check
```bash
curl http://localhost:8080/health
```

### Account Service (Port 8081 - Internal Only)

**POST /accounts/{accountId}/transactions** - Apply transaction
```bash
curl -X POST http://localhost:8081/accounts/ACC123/transactions \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-trace-123" \
  -d '{
    "transactionId": "txn-001",
    "type": "CREDIT",
    "amount": 100.0,
    "currency": "USD",
    "eventTimestamp": "2026-07-24T00:00:00Z"
  }'
```

**GET /accounts/{accountId}/balance** - Get balance
```bash
curl http://localhost:8081/accounts/ACC123/balance
```

**GET /accounts/{accountId}** - Get account details with transactions
```bash
curl http://localhost:8081/accounts/ACC123
```

## Testing

### Run Unit Tests

**account-service**
```bash
cd account-service
mvn test
```

**event-gateway**
```bash
cd event-gateway
mvn test
```

### Test Scenarios

**1. Idempotency Test**
```bash
# Submit same event twice
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-001","accountId":"ACC999","type":"CREDIT","amount":50.0,"currency":"USD","eventTimestamp":"2026-07-24T00:00:00Z"}'

curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-001","accountId":"ACC999","type":"CREDIT","amount":50.0,"currency":"USD","eventTimestamp":"2026-07-24T00:00:00Z"}'
# Second request returns 200 with X-Idempotent-Replay: true
```

**2. Balance Computation Test**
```bash
# Submit CREDIT and DEBIT transactions
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-002","accountId":"ACC888","type":"CREDIT","amount":200.0,"currency":"USD","eventTimestamp":"2026-07-24T00:00:00Z"}'

curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-003","accountId":"ACC888","type":"DEBIT","amount":50.0,"currency":"USD","eventTimestamp":"2026-07-24T00:01:00Z"}'

# Check balance (should be 150.0)
curl http://localhost:8080/accounts/ACC888/balance
```

**3. Circuit Breaker Test**
```bash
# Stop account-service
docker-compose stop account-service

# Submit event (will fail and return 503)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-004","accountId":"ACC777","type":"CREDIT","amount":100.0,"currency":"USD","eventTimestamp":"2026-07-24T00:00:00Z"}'
# Event persists with status ACCOUNT_SERVICE_UNAVAILABLE

# Restart account-service
docker-compose start account-service

# Re-submit same eventId (triggers retry)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-004","accountId":"ACC777","type":"CREDIT","amount":100.0,"currency":"USD","eventTimestamp":"2026-07-24T00:00:00Z"}'
# Now succeeds with status APPLIED
```

## Observability

### Metrics (Prometheus)

```bash
# account-service metrics
curl http://localhost:8081/actuator/prometheus | grep transactions_applied_total

# event-gateway metrics
curl http://localhost:8080/actuator/prometheus | grep events_received_total
```

**Key Metrics:**
- `transactions_applied_total{status="applied"}` - Successful new transactions
- `transactions_applied_total{status="duplicate"}` - Idempotent replays
- `events_received_total{status="success"}` - Events forwarded successfully
- `events_received_total{status="duplicate"}` - Duplicate event submissions
- `events_received_total{status="account_service_unavailable"}` - Events pending retry

### Logs (JSON)

Both services output structured JSON logs with:
- `service` field (account-service / event-gateway)
- `traceId` for request correlation
- Standard fields: timestamp, level, message, logger

```bash
# View logs
docker-compose logs -f event-gateway
docker-compose logs -f account-service
```

### Distributed Tracing

- event-gateway generates trace IDs (`X-Trace-Id` header)
- Trace ID propagated to account-service
- Trace ID included in all log entries
- Trace ID returned in response headers

## Project Structure

```
event-ledger-1/
├── account-service/
│   ├── src/main/java/com/example/account/
│   │   ├── model/          # Transaction entity
│   │   ├── repository/     # TransactionRepository
│   │   ├── dto/            # Request/Response DTOs
│   │   ├── service/        # AccountService
│   │   ├── controller/     # AccountController
│   │   ├── config/         # TraceIdFilter
│   │   └── exception/      # GlobalExceptionHandler
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── logback-spring.xml
│   ├── Dockerfile
│   └── pom.xml
├── event-gateway/
│   ├── src/main/java/com/example/gateway/
│   │   ├── model/          # Event entity
│   │   ├── repository/     # EventRepository
│   │   ├── dto/            # Request/Response DTOs
│   │   ├── service/        # EventService, AccountServiceClient
│   │   ├── controller/     # EventController
│   │   ├── config/         # RestClientConfig, TraceIdFilter
│   │   └── exception/      # GlobalExceptionHandler
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── logback-spring.xml
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
└── README.md
```

## Validation Rules

### Event/Transaction Request
- `eventId`/`transactionId`: Not blank
- `accountId`: Not blank (only on EventRequest)
- `type`: Must match pattern `CREDIT|DEBIT`
- `amount`: Positive number
- `currency`: Not blank
- `eventTimestamp`: Not null

Invalid requests return 400 with field-level error details.

## License

This is a take-home assessment project.
