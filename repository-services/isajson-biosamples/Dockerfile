# Builder stage: Use gradle image to compile jar file
FROM gradle:jdk17 AS builder

WORKDIR /app

COPY . .

WORKDIR /app/receipt
RUN ./gradlew build
RUN ./gradlew publishToMavenLocal

WORKDIR /app/isajson-biosamples
RUN ./gradlew build -x test -x spotlessApply --info --stacktrace

# Use a lightweight base image to run the jar file
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG JAR_FILE=/app/isajson-biosamples/build/libs/*.jar

# Copy the JAR file from the builder stage
COPY --from=builder ${JAR_FILE} app.jar

# Set the entry point for the application
ENTRYPOINT ["java", "-jar", "app.jar"]

