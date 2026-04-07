# CXForge Admin Tools Backend
# Spring Boot 3.2 + Java 21

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S cxforge && \
    adduser -u 1001 -S cxforge -G cxforge

# Add curl for health checks
RUN apk add --no-cache curl

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R cxforge:cxforge /app

# Switch to non-root user
USER cxforge

# Expose port
EXPOSE 10004

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:10004/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
