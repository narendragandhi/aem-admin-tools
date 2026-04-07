# AEM Admin Tools - Improvement Beads

This document breaks down all identified improvements into Steve Yegge's "beads" - small, independent, shippable units of work. Each bead can be completed in a focused session and delivers immediate value.

## Bead Philosophy

> "A bead is the smallest unit of work that can be shipped independently. It should be completable in one sitting, testable in isolation, and provide visible value." - Steve Yegge

---

## 🔴 Critical Beads (Security & Reliability)

### Bead C1: Remove Default Credentials
**Files**: `application.properties`, `application-prod.properties`
**Effort**: 30 min | **Risk**: Low | **Value**: High

- [ ] Remove default "admin" password for `aem.password`
- [ ] Remove default "admin" password for `security.admin.password`
- [ ] Add startup validation that fails if defaults are used in prod profile
- [ ] Update `.env.example` with placeholder values

### Bead C2: Add Rate Limiting
**Files**: New `RateLimitConfig.java`, `pom.xml`
**Effort**: 2 hrs | **Risk**: Low | **Value**: High

- [ ] Add Bucket4j dependency
- [ ] Create rate limit configuration (100 req/min default)
- [ ] Apply rate limiting to `/api/jobs` POST endpoint
- [ ] Add rate limit headers to responses
- [ ] Add configuration properties for customization

### Bead C3: Add Audit Logging
**Files**: New `AuditService.java`, `AuditLog.java`, `AuditLogRepository.java`
**Effort**: 3 hrs | **Risk**: Low | **Value**: High

- [ ] Create AuditLog entity with timestamp, user, action, details
- [ ] Create AuditService for logging events
- [ ] Add audit logging to job creation, cancellation, completion
- [ ] Add audit logging to tool execution
- [ ] Create `/api/audit` endpoint for viewing audit logs

### Bead C4: Add Circuit Breaker for AEM Calls
**Files**: `AemClientService.java`, `pom.xml`
**Effort**: 2 hrs | **Risk**: Medium | **Value**: High

- [ ] Add Resilience4j dependency
- [ ] Wrap AEM HTTP calls with circuit breaker
- [ ] Configure retry policy (3 retries, exponential backoff)
- [ ] Add fallback behavior when AEM is unavailable
- [ ] Add circuit breaker metrics to actuator

### Bead C5: Add Request Timeouts
**Files**: `AemClientService.java`, `LlmService.java`, `application.properties`
**Effort**: 1 hr | **Risk**: Low | **Value**: High

- [ ] Add configurable timeout for AEM HTTP calls (default 30s)
- [ ] Add configurable timeout for LLM API calls (default 60s)
- [ ] Add connection timeout configuration
- [ ] Add socket timeout configuration

---

## 🟡 Important Beads (Quality & Performance)

### Bead Q1: Add Unit Tests for AemClientService
**Files**: New `AemClientServiceTest.java`
**Effort**: 3 hrs | **Risk**: Low | **Value**: High

- [ ] Add WireMock dependency for HTTP mocking
- [ ] Test `testConnection()` success and failure
- [ ] Test `queryContent()` with various responses
- [ ] Test `getAssetMetadata()` parsing
- [ ] Test error handling scenarios
- [ ] Target: 80% coverage for AemClientService

### Bead Q2: Add Unit Tests for JobService
**Files**: New `JobServiceTest.java`
**Effort**: 2 hrs | **Risk**: Low | **Value**: High

- [ ] Test job creation and state transitions
- [ ] Test job cancellation
- [ ] Test job completion and failure
- [ ] Test concurrent job handling
- [ ] Test SSE event emission
- [ ] Target: 80% coverage for JobService

### Bead Q3: Add Unit Tests for Tool Implementations
**Files**: New `ContentHealthCheckToolTest.java`, `AssetReportToolTest.java`, `BulkTagManagerToolTest.java`
**Effort**: 4 hrs | **Risk**: Low | **Value**: High

- [ ] Test parameter validation for each tool
- [ ] Test tool execution with mocked AEM responses
- [ ] Test progress reporting
- [ ] Test result generation
- [ ] Target: 80% coverage for all tools

### Bead Q4: Add Integration Tests
**Files**: New `JobControllerIntegrationTest.java`, `ToolControllerIntegrationTest.java`
**Effort**: 3 hrs | **Risk**: Low | **Value**: Medium

- [ ] Add @SpringBootTest integration tests
- [ ] Test full job lifecycle via REST API
- [ ] Test tool discovery endpoint
- [ ] Test SSE streaming
- [ ] Test error responses

### Bead Q5: Add Caching for AEM Responses
**Files**: `AemClientService.java`, `CacheConfig.java`, `application.properties`
**Effort**: 2 hrs | **Risk**: Medium | **Value**: Medium

- [ ] Add Spring Cache dependency
- [ ] Create CacheConfig with Caffeine cache
- [ ] Add @Cacheable to `queryContent()` with 5-min TTL
- [ ] Add @Cacheable to `getAssetMetadata()` with 5-min TTL
- [ ] Add cache eviction on job completion
- [ ] Add cache metrics to actuator

### Bead Q6: Fix N+1 Query in ContentHealthCheckTool
**Files**: `ContentHealthCheckTool.java`
**Effort**: 2 hrs | **Risk**: Medium | **Value**: Medium

- [ ] Batch page property fetches (10 pages per request)
- [ ] Use AEM Query Builder for bulk operations
- [ ] Add progress logging for batches
- [ ] Maintain backward compatibility

### Bead Q7: Fix N+1 Query in AssetReportTool
**Files**: `AssetReportTool.java`
**Effort**: 2 hrs | **Risk**: Medium | **Value**: Medium

- [ ] Batch asset metadata fetches
- [ ] Use DAM query for bulk metadata
- [ ] Add progress logging for batches
- [ ] Maintain backward compatibility

### Bead Q8: Add Memory Limits for Job Storage
**Files**: `JobService.java`, `application.properties`
**Effort**: 1 hr | **Risk**: Low | **Value**: Medium

- [ ] Add maximum active jobs limit (default 100)
- [ ] Add job result size limit (default 10MB)
- [ ] Add scheduled cleanup for stale jobs (older than 24h)
- [ ] Add memory usage metrics

---

## 🟢 Enhancement Beads (Observability & Configuration)

### Bead E1: Add Structured Logging
**Files**: New `logback-spring.xml`, `pom.xml`
**Effort**: 1 hr | **Risk**: Low | **Value**: Medium

- [ ] Add logback-spring.xml configuration
- [ ] Configure JSON format for production profile
- [ ] Configure console format for development
- [ ] Add MDC for request tracing (correlationId)
- [ ] Configure log rotation (100MB, 30 days)

### Bead E2: Add Custom Metrics
**Files**: New `MetricsConfig.java`, services
**Effort**: 2 hrs | **Risk**: Low | **Value**: Medium

- [ ] Add job counter metrics (created, completed, failed, cancelled)
- [ ] Add job duration histogram
- [ ] Add AEM connection status gauge
- [ ] Add active jobs gauge
- [ ] Add tool execution counter by tool type

### Bead E3: Add Configuration Validation
**Files**: New `AemProperties.java`, `SecurityProperties.java`, `LlmProperties.java`
**Effort**: 2 hrs | **Risk**: Low | **Value**: Medium

- [ ] Create @ConfigurationProperties classes
- [ ] Add @Validated with JSR-380 annotations
- [ ] Add startup validation for required properties
- [ ] Add meaningful error messages for invalid config
- [ ] Update application.properties to use new classes

### Bead E4: Add OpenAPI Annotations
**Files**: `JobController.java`, `ToolController.java`, `AgentCardController.java`
**Effort**: 2 hrs | **Risk**: Low | **Value**: Low

- [ ] Add @Operation annotations to all endpoints
- [ ] Add @ApiResponse annotations for all status codes
- [ ] Add @Parameter annotations for path/query params
- [ ] Add @Schema annotations to DTOs
- [ ] Add request/response examples

### Bead E5: Extract HTTP Client Bean
**Files**: New `HttpClientConfig.java`, `AemClientService.java`, `LlmService.java`
**Effort**: 1 hr | **Risk**: Low | **Value**: Low

- [ ] Create configured HttpClient @Bean
- [ ] Configure connection pooling (max 50 connections)
- [ ] Configure keep-alive settings
- [ ] Inject HttpClient into services
- [ ] Remove inline HttpClient creation

### Bead E6: Create AbstractAdminTool Base Class
**Files**: New `AbstractAdminTool.java`, existing tools
**Effort**: 2 hrs | **Risk**: Medium | **Value**: Low

- [ ] Extract common patterns to AbstractAdminTool
- [ ] Add helper methods for progress reporting
- [ ] Add helper methods for AEM checks
- [ ] Refactor existing tools to extend base class
- [ ] Maintain backward compatibility

### Bead E7: Add Checkstyle Configuration
**Files**: New `checkstyle.xml`, `pom.xml`
**Effort**: 1 hr | **Risk**: Low | **Value**: Low

- [ ] Add Checkstyle Maven plugin
- [ ] Configure Google Java Style (modified)
- [ ] Add to CI pipeline
- [ ] Fix existing violations (or suppress)

### Bead E8: Add Dependabot Configuration
**Files**: New `.github/dependabot.yml`
**Effort**: 30 min | **Risk**: Low | **Value**: Low

- [ ] Configure weekly dependency updates for Maven
- [ ] Configure weekly dependency updates for npm
- [ ] Set auto-merge for patch versions
- [ ] Add reviewers for major/minor versions

---

## Implementation Order (Recommended)

### Phase 1: Security Hardening (Week 1)
1. **C1**: Remove Default Credentials
2. **C5**: Add Request Timeouts
3. **C2**: Add Rate Limiting
4. **C3**: Add Audit Logging

### Phase 2: Reliability (Week 2)
5. **C4**: Add Circuit Breaker for AEM Calls
6. **Q8**: Add Memory Limits for Job Storage
7. **E1**: Add Structured Logging
8. **E2**: Add Custom Metrics

### Phase 3: Testing (Week 3)
9. **Q1**: Add Unit Tests for AemClientService
10. **Q2**: Add Unit Tests for JobService
11. **Q3**: Add Unit Tests for Tool Implementations
12. **Q4**: Add Integration Tests

### Phase 4: Performance (Week 4)
13. **Q5**: Add Caching for AEM Responses
14. **Q6**: Fix N+1 Query in ContentHealthCheckTool
15. **Q7**: Fix N+1 Query in AssetReportTool
16. **E5**: Extract HTTP Client Bean

### Phase 5: Polish (Week 5)
17. **E3**: Add Configuration Validation
18. **E4**: Add OpenAPI Annotations
19. **E6**: Create AbstractAdminTool Base Class
20. **E7**: Add Checkstyle Configuration
21. **E8**: Add Dependabot Configuration

---

## Tracking Progress

| Bead | Status | Completed Date | Notes |
|------|--------|----------------|-------|
| C1 | 🔲 Not Started | | |
| C2 | 🔲 Not Started | | |
| C3 | 🔲 Not Started | | |
| C4 | 🔲 Not Started | | |
| C5 | 🔲 Not Started | | |
| Q1 | 🔲 Not Started | | |
| Q2 | 🔲 Not Started | | |
| Q3 | 🔲 Not Started | | |
| Q4 | 🔲 Not Started | | |
| Q5 | 🔲 Not Started | | |
| Q6 | 🔲 Not Started | | |
| Q7 | 🔲 Not Started | | |
| Q8 | 🔲 Not Started | | |
| E1 | 🔲 Not Started | | |
| E2 | 🔲 Not Started | | |
| E3 | 🔲 Not Started | | |
| E4 | 🔲 Not Started | | |
| E5 | 🔲 Not Started | | |
| E6 | 🔲 Not Started | | |
| E7 | 🔲 Not Started | | |
| E8 | 🔲 Not Started | | |

---

## Definition of Done

Each bead is considered complete when:
- [ ] Code changes are implemented
- [ ] Unit tests pass (if applicable)
- [ ] Integration tests pass (if applicable)
- [ ] Documentation is updated
- [ ] Code review approved
- [ ] Merged to main branch
