# CXForge Admin Tools Frontend
# Vite + Lit + Adobe Spectrum

FROM node:20-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S cxforge && \
    adduser -u 1001 -S cxforge -G cxforge

# Copy package files
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm ci

# Copy source
COPY . .

# Change ownership to non-root user
RUN chown -R cxforge:cxforge /app

# Switch to non-root user
USER cxforge

# Expose dev server port
EXPOSE 5174

# Start dev server with host binding for Docker
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
