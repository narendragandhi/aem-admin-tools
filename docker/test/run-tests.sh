#!/bin/sh
set -e

echo "=========================================="
echo "Running AEM Admin Tools Test Suite"
echo "=========================================="

# Run backend tests
echo ""
echo ">>> Running Backend Tests (Java/Maven)"
echo "=========================================="
cd /app/server
./mvnw test -B

# Run frontend tests
echo ""
echo ">>> Running Frontend Tests (Vitest)"
echo "=========================================="
cd /app/client
npm test

echo ""
echo "=========================================="
echo "All tests completed successfully!"
echo "=========================================="
