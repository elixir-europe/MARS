# Builder stage
FROM gradle:jdk17 AS builder

WORKDIR /app
# Copy everything from context (the repository-services folder)
COPY . .

ARG GPR_USER
ARG GPR_TOKEN

# Build receipt first (dependency)
# Verify receipt directory structure
RUN echo "=== Directory structure ===" && \
    ls -la /app && \
    echo "=== /app/receipt contents ===" && \
    (ls -la /app/receipt 2>&1 || echo "Directory not found or empty") && \
    echo "=== Finding gradlew ===" && \
    find /app -name "gradlew" -type f 2>/dev/null | head -5

WORKDIR /app/receipt
RUN test -f gradlew || (echo "ERROR: gradlew not in /app/receipt. Checking /app..." && ls -la /app && exit 1) && \
    sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck --info --stacktrace && \
    ./gradlew publishToMavenLocal

# Build isajson-ena
WORKDIR /app/isajson-ena
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    mkdir -p /root/.gradle && \
    echo "gpr.user=${GPR_USER}" >> /root/.gradle/gradle.properties && \
    echo "gpr.token=${GPR_TOKEN}" >> /root/.gradle/gradle.properties && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck --info --stacktrace


# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/isajson-ena/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
