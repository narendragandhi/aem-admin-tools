# Mock AEM Server for Admin Tools Testing
FROM node:20-alpine

WORKDIR /app

# Copy package files
COPY package.json ./

# Install dependencies
RUN npm install --production

# Copy server code
COPY . .

# Expose AEM default port
EXPOSE 4502

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:4502/health || exit 1

# Start server
CMD ["node", "server.js"]
