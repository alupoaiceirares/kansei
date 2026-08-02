# Build context is services/courier-one itself.
# Build with: docker build -t kansei-courier-one .

FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --omit=dev

COPY src ./src
COPY templates ./templates

CMD ["node", "src/index.js"]
