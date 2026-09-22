# syntax=docker/dockerfile:1.4
# Multi-stage Dockerfile for AutoFlow AI Flutter Web Application
# Optimized for high-speed delivery with Nginx Alpine

# -----------------------------------------------------------------------------
# Stage 1: Build Flutter Web Bundle
# -----------------------------------------------------------------------------
FROM ghcr.io/cirruslabs/flutter:3.27.1 AS builder
WORKDIR /app

# Enable web support and cache dependencies
RUN flutter config --enable-web
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

# Copy source code and compile optimized production web build
COPY . .
RUN flutter build web --release --pwa-strategy=none

# -----------------------------------------------------------------------------
# Stage 2: Serve with Nginx Alpine
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runner

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled Flutter web output
COPY --from=builder /app/build/web /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
