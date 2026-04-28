# tdd-stack-demo

A complete, runnable Spring Boot 3.5.13 / Java 21 reference project for
**Test-Driven Development against a real-world stack** — Postgres, Redis,
LocalStack (S3 + SQS), Keycloak (OIDC), and Temporal.io.

Every concept covered in the companion HTML tutorial is implemented as
working production code with an accompanying test class.

---

## Stack

| Component       | Version  | Port       | Used for                                |
| --------------- | -------- | ---------- | --------------------------------------- |
| Java            | 21 LTS   | —          | Language + virtual threads              |
| Spring Boot     | 3.5.13   | 8080       | Application framework                   |
| PostgreSQL      | 16       | 5432       | Primary store                           |
| Redis           | 7        | 6379       | Cache (`@Cacheable`)                    |
| LocalStack      | 3.8      | 4566       | S3 + SQS                                |
| Keycloak        | 26       | 8081       | OIDC / JWT issuer                       |
| Temporal        | 1.25     | 7233 / 8088| Workflow engine (server + UI)           |

---

## Prerequisites

- **JDK 21** — verify with `java -version`. Any distribution (Temurin, Zulu, GraalVM…) works.
- **Maven 3.9+** — verify with `mvn -version`. (Or use the wrapper: see notes below.)
- **Docker 24+** — verify with `docker --version`. Docker Compose v2 is required.
- **8 GB free RAM** — comfortable for running all containers concurrently.

> **No Maven installed?** This project does not bundle the Maven wrapper to keep
> the zip small. Either install Maven (`brew install maven`, `apt install maven`),
> or generate the wrapper once with `mvn wrapper:wrapper` and use `./mvnw` thereafter.

---

## Quick start

### 1. Boot the supporting services

```bash
docker compose up -d
docker compose ps                 # all services should be healthy in ~30s
```

Watch Keycloak finish importing its realm:

```bash
docker compose logs -f keycloak
# look for: "Realm 'tdd-app' imported"
```

### 2. Build & test (no infra needed for unit tests)

```bash
mvn test                          # runs JUnit 5 / Mockito tests
                                  # Testcontainers tests start their own
                                  # ephemeral containers — separate from
                                  # the long-running ones above
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The app comes up on `http://localhost:8080`. Endpoints:

- `GET  /actuator/health`                  — liveness/readiness (public)
- `POST /api/v1/orders`                    — create order  (requires `ROLE_USER`)
- `GET  /api/v1/orders/{id}`               — fetch order   (requires `ROLE_USER`)
- `GET  /api/v1/orders?status=NEW`         — list orders   (requires `ROLE_USER`)
- `DELETE /api/v1/orders/{id}`             — cancel order  (requires `ROLE_MANAGER` or `ROLE_ADMIN`)

### 4. Hit the API with a real Keycloak token

```bash
# Fetch a token for alice (ADMIN + USER roles)
TOKEN=$(curl -s -X POST \
  http://localhost:8081/realms/tdd-app/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=tdd-app-backend \
  -d client_secret=backend-secret-change-me \
  -d username=alice \
  -d password=password \
  | jq -r .access_token)

# Create an order
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USR-1",
    "items": [{"sku": "SKU-1", "quantity": 2, "unitPrice": 9.99}],
    "contactEmail": "alice@example.com",
    "orderDate": "2026-04-28"
  }' | jq
```

---

## Test users (Keycloak realm)

| Username | Password | Realm roles      |
| -------- | -------- | ---------------- |
| alice    | password | ADMIN, USER      |
| bob      | password | USER             |
| carol    | password | MANAGER, USER    |

Use them to verify role-based authorization:

- **alice** → ADMIN + USER. Can create, fetch, **and cancel** (cancel needs MANAGER or ADMIN).
- **bob**   → USER only. Can create &amp; fetch. **Cannot cancel** (gets 403).
- **carol** → MANAGER + USER. Can create, fetch, **and cancel**.

---

## Test catalogue

The `src/test/java` tree is organised by package, with one or more test
class per main class. Highlights:

```
src/test/java/com/example/tdd/
├── api/
│   ├── CreateOrderRequestValidationTest      # Bean Validation for every field rule
│   └── OrderControllerTest                   # @WebMvcTest + @MockitoBean + @WithMockUser
├── async/
│   ├── OrderEnrichmentServiceTest            # CompletableFuture w/ same-thread executor
│   └── FanoutServiceTest                     # Virtual thread fanout against WireMock
├── cache/
│   └── OrderServiceCacheTest                 # @Cacheable hit/eviction with REAL Redis
├── client/
│   ├── ResilientPaymentClientTest            # @Retryable retry + @Recover fallback
│   └── UserClientTest                        # 200 / 404 / 503 / malformed JSON
├── domain/
│   └── OrderRepositoryTest                   # @DataJpaTest + Postgres Testcontainer
├── e2e/
│   └── CreateOrderE2ETest                    # Postgres + Redis + S3 + SQS in one flow
├── feature/
│   └── FeatureServiceTest                    # ReflectionTestUtils for @Value + private methods
├── messaging/
│   └── OrderEventPublisherTest               # SQS roundtrip via LocalStack
├── storage/
│   └── InvoiceStorageServiceTest             # S3 PUT/GET + presigned URL via LocalStack
├── workflow/
│   └── OrderFulfillmentWorkflowTest          # Temporal TestWorkflowExtension (in-process)
└── support/
    └── OrderFixtures                          # Reusable test data
```

### Run subset patterns

```bash
mvn test                                     # everything
mvn test -Dtest=OrderControllerTest          # one class
mvn test -Dtest='OrderControllerTest#cancel*'  # one method pattern
mvn test -Dgroups=fast                       # JUnit tag filter (if added)
```

### CI tip — share Testcontainers across runs

Add to `~/.testcontainers.properties`:
```
testcontainers.reuse.enable=true
```

Then containers marked with `.withReuse(true)` stay alive between local
test runs. Cuts a 90-second suite to under 10 seconds. Always disabled in CI.

---

## Project structure

```
.
├── README.md                         # this file
├── docker-compose.yml                # 7-service stack
├── pom.xml                           # Maven, all dependencies pinned
├── .gitignore
├── keycloak/
│   └── realm-export.json             # imported on Keycloak start
├── scripts/
│   ├── init-multi-db.sh              # creates appdb / temporal / keycloak DBs
│   └── init-localstack.sh            # creates buckets + queues
├── src/main/java/com/example/tdd/
│   ├── TddStackApplication.java
│   ├── api/                          # REST controllers, exception handler, DTOs
│   ├── async/                        # CompletableFuture, virtual-thread fanout
│   ├── cache/                        # (cache lives on OrderService directly)
│   ├── client/                       # Resilient HTTP clients
│   ├── config/                       # Security, AWS, cache, async, REST configs
│   ├── domain/
│   │   ├── exception/                # Domain exceptions
│   │   └── order/                    # Aggregate, repository, service
│   ├── feature/                      # FeatureService — ReflectionTestUtils target
│   ├── messaging/                    # OrderEvent + publisher + listener
│   ├── storage/                      # InvoiceStorageService — S3
│   └── workflow/                     # Temporal workflow + activities
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__initial_schema.sql
└── src/test/...
```

---

## What each package demonstrates

| Package      | Demonstrates                                                           |
| ------------ | ---------------------------------------------------------------------- |
| `api`        | Validation, exception mapping, role-based auth via `@PreAuthorize`     |
| `async`      | CompletableFuture composition; Java 21 virtual threads                 |
| `client`     | Spring Retry; structured upstream HTTP error handling                  |
| `domain`     | JPA entity with optimistic locking + JSONB column                      |
| `feature`    | Private `@Value` injection via `ReflectionTestUtils`; retry loop       |
| `messaging`  | SQS publisher with raw SDK; listener via Spring Cloud AWS              |
| `storage`    | S3 upload/download + presigned URLs against LocalStack                 |
| `workflow`   | Temporal workflow with signals, queries, activity retries              |

---

## Common pitfalls

1. **Containers fail to start** — make sure Docker has at least 4 CPUs and 8 GB allocated.
2. **Port already in use** — another service is on 5432/6379/4566/8081/7233. Stop it or change the port mapping in `docker-compose.yml`.
3. **Keycloak 401 on token request** — wait for `Realm 'tdd-app' imported` in logs (~20s after start).
4. **Tests fail with "Could not find a valid Docker environment"** — ensure your Docker daemon is reachable (`docker info`).
5. **`forcePathStyle(true)` missing** — required for any S3 client that talks to LocalStack. Already set in `AwsConfig`.
6. **Temporal worker is disabled** — `temporal.enabled=false` by default. Workflow tests use the in-process `TestWorkflowExtension`, no server needed. Set `temporal.enabled=true` and start the `temporal` container before using the production worker.

---

## Tearing it down

```bash
docker compose down -v             # also removes volumes
```

---

## Companion tutorial

The full HTML walkthrough — diagrams, deep dives, references — is the file
**`tutorial.html`** that ships alongside this project in the same archive.
Open it in any browser; everything runs offline.

---

## License

This is reference material. Use it however helps you ship better tests.
# javatest101
