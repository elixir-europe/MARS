# --------------------------------------------------------------------
# Stage 1: Build mars-repository-lib and publish to a local Maven repo
# --------------------------------------------------------------------
FROM gradle:7.2-jdk11 AS lib-builder

WORKDIR /lib
USER root

# Install git and clone the mars-repository-lib repository
RUN apk add --no-cache git || apt-get update && apt-get install -y git
RUN git clone https://github.com/elixir-europe/mars-repository-lib.git

# Build and publish mars-repository-lib to the local Maven repository
WORKDIR /lib/mars-repository-lib
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew publishToMavenLocal --no-daemon --info --stacktrace

# ---------------------------------------------
# Stage 2: Use gradle image to compile jar file
# ---------------------------------------------
FROM gradle:jdk17 AS builder

WORKDIR /app
USER root

# Bring in the published artifact from Stage 1
COPY --from=lib-builder /root/.gradle/caches /root/.gradle/caches
COPY --from=lib-builder /root/.m2 /root/.m2
COPY repository-services/isajson-biosamples /app/isajson-biosamples

# Build isajson-biosamples
WORKDIR /app/isajson-biosamples
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck --info --stacktrace

# Use a lightweight base image to run the jar file
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG JAR_FILE=/app/isajson-biosamples/build/libs/*.jar

# Copy the JAR file from the builder stage
COPY --from=builder ${JAR_FILE} app.jar

# Set the entry point for the application
ENTRYPOINT ["java", "-jar", "app.jar"]

