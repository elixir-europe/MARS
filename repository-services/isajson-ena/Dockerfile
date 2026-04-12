# Builder stage
FROM gradle:jdk17 AS builder

WORKDIR /app

USER root
COPY repository-services/receipt /app/receipt
COPY repository-services/isajson-ena /app/isajson-ena

# Build receipt first (dependency)
WORKDIR /app/receipt
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build && \
    ./gradlew publishToMavenLocal

# Build isajson-ena
WORKDIR /app/isajson-ena
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck --info --stacktrace

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/isajson-ena/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
