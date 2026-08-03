# Build context is services/courier-one itself
# Build with: docker build -t kansei-courier-one .

FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --omit=dev

COPY src ./src
COPY templates ./templates

# No curl/wget on this base image - Node itself does the probe. Ready only once consumeMailEvents has fully connected and attached its consumer (see src/health.js)
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://localhost:' + process.env.HEALTH_PORT, res => process.exit(res.statusCode === 200 ? 0 : 1)).on('error', () => process.exit(1))"

CMD ["node", "src/index.js"]
