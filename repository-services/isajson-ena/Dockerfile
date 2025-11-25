# Builder stage
FROM gradle:jdk17 AS builder

WORKDIR /app
# Copy receipt directory first (dependency)
COPY receipt /app/receipt
# Copy isajson-ena directory
COPY isajson-ena /app/isajson-ena

ARG GPR_USER
ARG GPR_TOKEN

# Build receipt first (dependency)
WORKDIR /app/receipt
RUN if [ ! -f gradlew ]; then \
        echo "ERROR: gradlew not found in /app/receipt"; \
        echo "Checking if receipt files are in a different location..."; \
        find /app -name "gradlew" -type f 2>/dev/null; \
        echo "Contents of /app/receipt:"; \
        ls -la /app/receipt 2>&1 || echo "Directory empty or doesn't exist"; \
        exit 1; \
    fi && \
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
