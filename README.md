# AEM Admin Tools

A modern, extensible admin tools platform for Adobe Experience Manager, inspired by ACS AEM Commons MCP tools.

Built with:
- **Backend**: Spring Boot 3.2 + Java 21
- **Frontend**: Lit Web Components + Adobe Spectrum
- **Streaming**: Server-Sent Events (SSE) for real-time progress

## Features

- **Plugin Architecture**: Easy to add new admin tools
- **Real-time Progress**: SSE streaming for live job updates
- **Modern UI**: Adobe Spectrum design system
- **Demo Mode**: Works without AEM connection for testing

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

## Architecture

```
aem-admin-tools/
├── server/                      # Spring Boot backend
│   └── src/main/java/com/adobe/aem/admintools/
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

Edit `server/src/main/resources/application.properties`:

```properties
# Server port
server.port=10004

# AEM Connection (optional)
aem.enabled=true
aem.author.url=http://localhost:4502
aem.username=admin
aem.password=admin

# Job settings
jobs.max-concurrent=3
```

## Comparison with ACS AEM Commons MCP

| Feature | ACS MCP | AEM Admin Tools |
|---------|---------|-----------------|
| Runs inside AEM | Yes | No (external) |
| Real-time progress | Yes | Yes (SSE) |
| Modern UI | No | Yes (Spectrum) |
| Custom tools | Yes (Java) | Yes (Spring) |
| Works without AEM | No | Yes (demo mode) |
| AI enhancement | No | Planned |

## Future Enhancements

- [ ] AEM SDK integration for real operations
- [ ] AI-powered suggestions (find issues, recommend fixes)
- [ ] Scheduled job execution
- [ ] Export results to CSV/JSON
- [ ] Multi-user job management
- [ ] Webhook notifications
