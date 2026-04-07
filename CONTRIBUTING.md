# Contributing to AEM Admin Tools

Thank you for your interest in contributing to AEM Admin Tools! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.9+
- Node.js 20+ (for frontend)
- Docker & Docker Compose (for containerized development)

### Development Setup

#### Option 1: Docker (Recommended)

```bash
# Start everything with Docker
docker-compose --profile full up -d

# Or start just the backend
docker-compose --profile backend up -d

# Or use mock AEM for testing
docker-compose --profile mock up -d
```

#### Option 2: Local Development

**Backend:**
```bash
cd server
mvn spring-boot:run
```

**Frontend:**
```bash
cd client
npm install
npm run dev
```

Access the UI at http://localhost:5174

## Project Structure

```
aem-admin-tools/
├── server/                     # Spring Boot backend
│   └── src/main/java/com/adobe/aem/admintools/
│       ├── tool/              # Tool implementations
│       ├── service/           # Business logic
│       ├── controller/        # REST endpoints
│       └── model/             # Data models
│
├── client/                    # Lit + Spectrum frontend
│   └── src/
│       ├── components/        # Web components
│       └── types.ts           # TypeScript types
│
└── docker/                    # Docker configurations
    ├── mock-aem/             # Mock AEM server
    └── test/                 # Test runner
```

## Creating a New Tool

1. Create a new class implementing `AdminTool`:

```java
@Component
public class MyTool implements AdminTool {

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .id("my-tool")
            .name("My Tool")
            .description("What it does")
            .category("Content")  // or "Assets", etc.
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
    public void execute(Job job, Consumer<Job> progressCallback) {
        // Tool logic here
        job.addLog(JobLogEntry.Level.INFO, "Starting...");

        // Update progress
        job.setTotalItems(100);
        for (int i = 0; i < 100; i++) {
            // Do work...
            job.addResult(JobResult.builder()
                .path("/content/page" + i)
                .status(JobResult.ResultStatus.SUCCESS)
                .message("Processed")
                .build());
            progressCallback.accept(job);
        }
    }
}
```

2. Add tests for your tool in `server/src/test/java/`

3. Update documentation

## Running Tests

### Backend Tests

```bash
cd server
mvn test
```

### Frontend Tests

```bash
cd client
npm test
```

### All Tests with Docker

```bash
docker-compose --profile test up --abort-on-container-exit
```

## Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]
```

**Types:**
- `feat`: New feature or tool
- `fix`: Bug fix
- `docs`: Documentation only
- `test`: Adding tests
- `refactor`: Code refactoring
- `chore`: Maintenance tasks

**Examples:**
```
feat(tool): add bulk page mover tool
fix(health-check): handle missing jcr:content gracefully
docs(readme): add Docker setup instructions
test(job-service): add concurrent job tests
```

## Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-tool`
3. Make your changes with tests
4. Run all tests: `mvn test && npm test`
5. Commit following the message format
6. Push and create a Pull Request

### PR Checklist

- [ ] Tests pass
- [ ] Code follows project style
- [ ] New tool has documentation
- [ ] No merge conflicts

## Coding Standards

### Java

- Follow Google Java Style Guide
- Use Lombok for boilerplate reduction
- Add Javadoc for public methods
- Use `@Slf4j` for logging

### TypeScript/JavaScript

- Use TypeScript for type safety
- Follow existing Lit component patterns
- Document complex logic

### API Design

- RESTful endpoints
- Use proper HTTP methods and status codes
- Return consistent JSON responses

## Tool Categories

When creating tools, use appropriate categories:

- **Content**: Page management, health checks, bulk operations
- **Assets**: DAM operations, metadata, reports
- **Tags**: Tag management, taxonomy operations
- **Workflows**: Workflow management, automation
- **System**: Configuration, maintenance

## Questions?

- Open an issue for questions
- Check existing issues before creating new ones

## License

By contributing, you agree that your contributions will be licensed under the project's license.
