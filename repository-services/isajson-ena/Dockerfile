# Builder stage
FROM gradle:jdk17 AS builder
ARG MARS_REPOSITORY_LIB_REF=v0.0.2

WORKDIR /lib
USER root

# Install git and clone the mars-repository-lib repository
RUN apk add --no-cache git || apt-get update && apt-get install -y git
RUN git clone --depth 1 --branch "${MARS_REPOSITORY_LIB_REF}" https://github.com/elixir-europe/mars-repository-lib.git

# Build and publish mars-repository-lib to the local Maven repository
WORKDIR /lib/mars-repository-lib
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build && \
    ./gradlew publishToMavenLocal

WORKDIR /app
# Copy the isajson-ena source code to the builder stage
COPY repository-services/isajson-ena /app/isajson-ena

# Build isajson-ena
WORKDIR /app/isajson-ena
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew build -x test -x spotlessApply -x spotlessJavaCheck --info --stacktrace

# Use a multi-arch JRE base image to run the jar file
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/isajson-ena/build/libs/*.jar app.jar

# Set the entry point for the application
ENTRYPOINT ["java", "-jar", "app.jar"]
