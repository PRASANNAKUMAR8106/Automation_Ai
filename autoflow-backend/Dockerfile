# syntax=docker/dockerfile:1.4
# Multi-stage Dockerfile for AutoFlow AI Spring Boot 3.3+ Backend (Java 21 LTS)
# Optimized for cost-aware modular-monolith single-VM production hosting

# -----------------------------------------------------------------------------
# Stage 1: Build & Package JAR
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Install bash and dos2unix for build scripting
RUN apk add --no-cache bash

# Cache Gradle wrapper and dependencies
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build production bootJar
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Extract layered JAR for optimal Docker layer caching
WORKDIR /workspace/extracted
RUN java -Djarmode=tools -jar /workspace/build/libs/*.jar extract --layers --destination .

# -----------------------------------------------------------------------------
# Stage 2: Minimal Distroless / Alpine Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

# Create dedicated non-root application user
RUN addgroup -S autoflow && adduser -S autoflow -G autoflow

WORKDIR /opt/autoflow

# Copy extracted layers in order of change frequency
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

# Chown application directory
RUN chown -R autoflow:autoflow /opt/autoflow

USER autoflow:autoflow

# Environment variables tuned for container memory boundaries
ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=40.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=prod"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
