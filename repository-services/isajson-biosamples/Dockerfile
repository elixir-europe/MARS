# Builder stage: Use gradle image to compile jar file
FROM gradle:jdk17 AS builder

# Set Gradle options for Docker builds
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=false"

WORKDIR /app

COPY . .

# Make gradlew scripts executable
RUN chmod +x receipt/gradlew isajson-biosamples/gradlew

WORKDIR /app/receipt
RUN ./gradlew build --no-daemon --stacktrace
RUN ./gradlew publishToMavenLocal --no-daemon

WORKDIR /app/isajson-biosamples
RUN ./gradlew build -x test -x spotlessApply --no-daemon --stacktrace

# Use a lightweight base image to run the jar file
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG JAR_FILE=/app/isajson-biosamples/build/libs/*.jar

# Copy the JAR file from the builder stage
COPY --from=builder ${JAR_FILE} app.jar

# Set the entry point for the application
ENTRYPOINT ["java", "-jar", "app.jar"]

