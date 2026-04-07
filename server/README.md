# CXForge Admin Tools

Modern admin tools for Adobe Experience Manager (AEM) with AI enhancement.

## Features

- **Bulk Content Operations**: Move, copy, delete content across AEM instances
- **Asset Management**: Bulk metadata updates, asset health checks
- **Content Health Checks**: Validate content structure and references
- **AI-Enhanced Tools**: LLM-powered content analysis (Ollama, OpenAI, Anthropic)
- **Real-time Streaming**: Live job progress via SSE
- **AG-UI Protocol**: Compatible with agent orchestration systems

## Enterprise Features

- **OAuth2/OIDC Authentication**: JWT-based auth with role extraction (Keycloak, Okta, etc.)
- **Multi-tenancy**: Tenant isolation via JWT claims or headers
- **Distributed Tracing**: OpenTelemetry integration with W3C TraceContext
- **Job Resilience**: Automatic retry with exponential backoff + Dead Letter Queue
- **Horizontal Scaling**: Redis-backed distributed locking and rate limiting
- **Structured Logging**: JSON logs with trace correlation for observability

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- AEM instance (Author/Publish)
- Optional: Ollama for local LLM

### Development

```bash
# Clone and build
cd server
mvn clean install

# Run with H2 in-memory database
mvn spring-boot:run
```

Access:
- API: http://localhost:10004
- Swagger UI: http://localhost:10004/swagger-ui.html
- H2 Console: http://localhost:10004/h2-console

### Production

```bash
# Build
mvn clean package -DskipTests

# Run with production profile
java -jar target/admin-tools-1.0.0.jar --spring.profiles.active=prod
```

## Configuration

All configuration via environment variables:

### Required for Production

| Variable | Description | Example |
|----------|-------------|---------|
| `SECURITY_ENABLED` | Enable authentication | `true` |
| `SECURITY_ADMIN_PASSWORD` | Admin password (8+ chars) | `SecurePass123!` |
| `AEM_AUTHOR_URL` | AEM Author URL | `https://author.example.com` |
| `AEM_PASSWORD` | AEM admin password | `aem-password` |
| `DATABASE_URL` | PostgreSQL connection | `jdbc:postgresql://host:5432/admintools` |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `10004` | Server port |
| `AGENT_BASE_URL` | `http://localhost:10004` | Public URL for agent card |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5174` | Allowed CORS origins |
| `LLM_ENABLED` | `true` | Enable LLM features |
| `LLM_PROVIDER` | `ollama` | LLM provider: ollama, openai, anthropic |
| `LLM_OLLAMA_URL` | `http://localhost:11434` | Ollama server URL |
| `RATE_LIMIT_ENABLED` | `true` | Enable rate limiting |
| `RATE_LIMIT_REQUESTS_PER_MINUTE` | `100` | General rate limit |

### Enterprise Authentication (OAuth2/OIDC)

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH2_ENABLED` | `false` | Enable OAuth2/JWT authentication |
| `OAUTH2_ISSUER_URI` | - | OIDC issuer URI (e.g., `https://keycloak.example.com/realms/myrealm`) |
| `OAUTH2_JWK_SET_URI` | - | JWKS endpoint for token validation |
| `OAUTH2_TENANT_CLAIM` | `tenant_id` | JWT claim containing tenant ID |
| `OAUTH2_ROLES_CLAIM` | `roles` | JWT claim containing user roles |

### Multi-tenancy

| Variable | Default | Description |
|----------|---------|-------------|
| `MULTITENANCY_ENABLED` | `false` | Enable multi-tenant isolation |
| `MULTITENANCY_DEFAULT_TENANT` | `default` | Default tenant for unauthenticated requests |

### Distributed Infrastructure (Redis)

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_ENABLED` | `false` | Enable Redis for distributed state |
| `REDIS_HOST` | `localhost` | Redis server host |
| `REDIS_PORT` | `6379` | Redis server port |
| `REDIS_PASSWORD` | - | Redis password |

### Distributed Tracing

| Variable | Default | Description |
|----------|---------|-------------|
| `TRACING_ENABLED` | `false` | Enable distributed tracing |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Sampling rate (0.0-1.0) |
| `OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP collector endpoint |

## Docker

```bash
# Build
docker build -t cxforge/admin-tools .

# Run
docker run -d \
  -p 10004:10004 \
  -e SECURITY_ENABLED=true \
  -e SECURITY_ADMIN_PASSWORD=YourSecurePassword \
  -e AEM_AUTHOR_URL=https://author.example.com \
  -e AEM_PASSWORD=aem-password \
  -e DATABASE_URL=jdbc:postgresql://db:5432/admintools \
  cxforge/admin-tools
```

## API Endpoints

### Core API (v1)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/tools` | GET | List available tools |
| `/api/v1/jobs` | POST | Create a new job |
| `/api/v1/jobs/{id}` | GET | Get job status |
| `/api/v1/jobs/{id}/stream` | GET | Stream job updates (SSE) |
| `/api/v1/jobs/{id}/cancel` | POST | Cancel a running job |

### Dead Letter Queue (Admin)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/jobs/dlq` | GET | List failed jobs |
| `/api/v1/jobs/dlq/stats` | GET | DLQ statistics |
| `/api/v1/jobs/dlq/{id}/retry` | POST | Retry a failed job |
| `/api/v1/jobs/dlq/{id}/discard` | POST | Discard a failed job |

### Audit (Admin)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/audit` | GET | Recent audit logs |
| `/api/v1/audit/user/{username}` | GET | Logs by user |
| `/api/v1/audit/action/{action}` | GET | Logs by action type |

### System

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/.well-known/agent-card.json` | GET | AG-UI agent card |
| `/actuator/health` | GET | Health check |
| `/actuator/prometheus` | GET | Prometheus metrics |

## Monitoring

### Health Check
```bash
curl http://localhost:10004/actuator/health
```

### Prometheus Metrics
```bash
curl http://localhost:10004/actuator/prometheus
```

Key metrics:
- `admintools_jobs_created_total` - Jobs created
- `admintools_jobs_completed_total` - Jobs completed
- `admintools_jobs_failed_total` - Jobs failed
- `admintools_jobs_retried_total` - Jobs retried
- `admintools_jobs_active` - Currently running jobs
- `admintools_jobs_dlq_size` - Jobs in dead letter queue
- `admintools_aem_circuitbreaker_state` - AEM circuit breaker (0=closed, 1=open, 2=half-open)
- `admintools_ratelimit_rejected_total` - Rate-limited requests

## Architecture

### Horizontal Scaling

With Redis enabled, multiple instances can run behind a load balancer:

```
                    ┌─────────────┐
                    │   Load      │
                    │  Balancer   │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌────────────┐
    │  Instance  │  │  Instance  │  │  Instance  │
    │     1      │  │     2      │  │     3      │
    └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
          │               │               │
          └───────────────┼───────────────┘
                          ▼
                   ┌────────────┐
                   │   Redis    │  (Distributed locks,
                   │            │   Rate limiting)
                   └────────────┘
                          │
                   ┌────────────┐
                   │ PostgreSQL │  (Job state,
                   │            │   Audit logs)
                   └────────────┘
```

### Job Lifecycle with Retry

```
CREATE → PENDING → RUNNING → [SUCCESS] → COMPLETED
                     │
                     ├── [Transient Error] → RETRY (backoff) → RUNNING
                     │
                     └── [Max Retries] → FAILED → DLQ
```

## Security

- HTTP Basic Authentication (production)
- OAuth2/OIDC JWT validation (enterprise)
- Rate limiting per IP (Bucket4j)
- Circuit breaker for AEM/LLM calls (Resilience4j)
- Security headers (CSP, HSTS, X-Frame-Options)
- Audit logging with trace correlation

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JobServiceTest

# Run with coverage
mvn test jacoco:report
```

**Test Suite**: 50 unit tests covering:
- Controller layer (JobController, ToolController, DlqController)
- Service layer (JobService, ToolRegistry)
- Tool implementations (ContentHealthCheckTool)

## Project Structure

```
src/main/java/io/cxforge/admintools/
├── config/           # Spring configuration (Security, Redis, Tracing)
├── controller/       # REST controllers (v1 API)
├── exception/        # Custom exceptions and global handler
├── filter/           # HTTP filters (RateLimit, Tenant, Tracing)
├── model/            # Domain models and DTOs
├── repository/       # JPA repositories
├── service/          # Business logic services
└── tool/             # Admin tool implementations
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run tests (`mvn test`)
4. Commit changes (`git commit -m 'Add amazing feature'`)
5. Push to branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

## License

Proprietary - CXForge
