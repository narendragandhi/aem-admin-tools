# CXForge Admin Tools

A modern, extensible admin tools platform for Adobe Experience Manager, inspired by ACS AEM Commons MCP tools. **CXForge** (Content Experience Forge) is your toolkit for crafting and managing AEM content at scale.

Built with:
- **Backend**: Spring Boot 3.2 + Java 21
- **Frontend**: Lit Web Components + Adobe Spectrum
- **Streaming**: Server-Sent Events (SSE) for real-time progress

## Features

- **Plugin Architecture**: Easy to add new admin tools
- **Real-time Progress**: SSE streaming for live job updates
- **Modern UI**: Adobe Spectrum design system
- **Demo Mode**: Works without AEM connection for testing
- **Security**: Configurable authentication, CORS, and security headers
- **Docker Ready**: Non-root containers for production deployments

## Included Tools

### Content Tools
1. **Content Health Check** - Scan content for issues:
   - Missing titles/descriptions
   - Broken references
   - Stale content
   - Missing alt text
   - Unpublished changes

2. **Bulk Tag Manager** - Manage tags at scale:
   - Find pages with specific tags
   - Add/remove tags in bulk
   - Replace one tag with another

### Asset Tools
3. **Asset Report** - Generate DAM reports:
   - Asset inventory by type
   - Missing metadata analysis
   - Large file finder
   - Unused asset detection

## Quick Start

### Start the Backend (Port 10004)

```bash
cd server
mvn spring-boot:run
```

### Start the Frontend (Port 5174)

```bash
cd client
npm install
npm run dev
```

Open http://localhost:5174 in your browser.

### Docker Compose

```bash
# Start full stack
docker-compose --profile full up

# Backend only
docker-compose --profile backend up
```

## Architecture

```
cxforge-admin-tools/
├── server/                      # Spring Boot backend
│   └── src/main/java/io/cxforge/admintools/
│       ├── tool/               # Tool interface & implementations
│       │   ├── AdminTool.java  # Base interface for all tools
│       │   ├── ContentHealthCheckTool.java
│       │   ├── BulkTagManagerTool.java
│       │   └── AssetReportTool.java
│       ├── service/
│       │   ├── ToolRegistry.java   # Tool plugin registry
│       │   └── JobService.java     # Job execution & streaming
│       ├── controller/
│       │   ├── ToolController.java # Tool discovery API
│       │   └── JobController.java  # Job management API
│       ├── config/             # Configuration classes
│       │   ├── SecurityConfig.java # Auth & security headers
│       │   └── WebConfig.java      # CORS configuration
│       └── model/              # Data models
│
└── client/                     # Lit + Spectrum frontend
    └── src/
        ├── components/
        │   ├── admin-tools-app.ts  # Main app shell
        │   ├── tool-list.ts        # Tool catalog
        │   ├── tool-form.ts        # Dynamic parameter form
        │   ├── job-progress.ts     # Real-time progress
        │   ├── job-results.ts      # Results table
        │   └── job-history.ts      # Recent jobs
        └── types.ts            # TypeScript interfaces

```

## Creating a New Tool

1. Implement the `AdminTool` interface:

```java
@Component
public class MyCustomTool implements AdminTool {

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .id("my-custom-tool")
            .name("My Custom Tool")
            .description("Does something useful")
            .category("Content")
            .parameters(List.of(
                ToolParameter.builder()
                    .name("path")
                    .label("Content Path")
                    .type(ToolParameter.ParameterType.PATH)
                    .required(true)
                    .build()
            ))
            .build();
    }

    @Override
    public String validateParameters(Map<String, Object> params) {
        // Return null if valid, error message otherwise
        return null;
    }

    @Override
    public void execute(Job job) {
        // Your tool logic here
        // Update job.addResult() and job.addLog() as you go
        // Call jobService.emitUpdate(job) to stream progress
    }
}
```

The tool is automatically registered via Spring's component scanning.

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/tools` | GET | List all available tools |
| `/api/tools/{id}` | GET | Get tool definition |
| `/api/jobs` | POST | Create and start a job |
| `/api/jobs` | GET | List recent jobs |
| `/api/jobs/{id}` | GET | Get job status |
| `/api/jobs/{id}/stream` | GET (SSE) | Stream job updates |
| `/api/jobs/{id}/cancel` | POST | Cancel running job |

## Configuration

### Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Key configuration options:

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Backend server port | `10004` |
| `AEM_ENABLED` | Enable AEM connection | `true` |
| `AEM_AUTHOR_URL` | AEM author URL | `http://localhost:4502` |
| `AEM_USERNAME` | AEM username | `admin` |
| `AEM_PASSWORD` | AEM password | `admin` |
| `SECURITY_ENABLED` | Enable authentication | `false` |
| `SECURITY_ADMIN_PASSWORD` | Admin password (when security enabled) | `admin` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:5174,http://localhost:8081` |
| `LLM_PROVIDER` | AI provider (ollama/openai/anthropic) | `ollama` |
| `LOG_LEVEL` | Application log level | `INFO` |

### Production Security

For production deployments, enable security:

```bash
export SECURITY_ENABLED=true
export SECURITY_ADMIN_PASSWORD=<strong-password>
export AEM_PASSWORD=<aem-password>
export CORS_ALLOWED_ORIGINS=https://your-domain.com
```

Security features when enabled:
- HTTP Basic Authentication
- HSTS (Strict Transport Security)
- Content Security Policy (CSP)
- X-Frame-Options: DENY
- Referrer Policy
- Permissions Policy

### API Documentation

When running, access the Swagger UI at:
- http://localhost:10004/swagger-ui.html
- http://localhost:10004/v3/api-docs (OpenAPI spec)

### Health & Metrics

Spring Boot Actuator endpoints:
- `/actuator/health` - Health check
- `/actuator/info` - Application info
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics

## Comparison with ACS AEM Commons MCP

| Feature | ACS MCP | CXForge |
|---------|---------|---------|
| Runs inside AEM | Yes | No (external) |
| Real-time progress | Yes | Yes (SSE) |
| Modern UI | No | Yes (Spectrum) |
| Custom tools | Yes (Java) | Yes (Spring) |
| Works without AEM | No | Yes (demo mode) |
| AI enhancement | No | Planned |
| Security headers | N/A | Yes |
| Container ready | No | Yes |

## Future Enhancements

- [ ] AEM SDK integration for real operations
- [ ] AI-powered suggestions (find issues, recommend fixes)
- [ ] Scheduled job execution
- [ ] Export results to CSV/JSON
- [ ] Multi-user job management
- [ ] Webhook notifications
- [ ] OAuth2/OIDC authentication
- [ ] Database persistence for job history
- [ ] Rate limiting

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Security

For security issues, please see [SECURITY.md](SECURITY.md).
