# Builder stage
FROM gradle:jdk17 AS builder

WORKDIR /app
# Copy everything from context (the repository-services folder)
COPY . .

ARG GPR_USER
ARG GPR_TOKEN

# Build receipt first (dependency)
WORKDIR /app/receipt
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck && \
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
