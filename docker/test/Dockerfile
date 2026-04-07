# Test Runner Container for AEM Admin Tools
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install Node.js for frontend tests
RUN apk add --no-cache nodejs npm curl

# Copy Maven wrapper and pom
COPY server/mvnw ./server/
COPY server/.mvn ./server/.mvn/
COPY server/pom.xml ./server/

# Copy source for backend tests
COPY server/src ./server/src/

# Copy frontend for tests
COPY client ./client/

# Install frontend dependencies
RUN cd client && npm ci

# Make mvnw executable
RUN chmod +x server/mvnw

# Run tests script
COPY docker/test/run-tests.sh .
RUN chmod +x run-tests.sh

CMD ["./run-tests.sh"]
