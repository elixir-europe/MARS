# =========================
# Builder stage: compile JAR
# =========================
FROM gradle:jdk11 AS builder

# Set working directory inside the image
WORKDIR /app

# Copy the service code into the image
COPY isajson-ena ./isajson-ena

# Mount GitHub Packages credentials as secrets and write to gradle.properties
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    mkdir -p /root/.gradle && \
    echo "gpr.user=$(cat /run/secrets/gpr_user)" >> /root/.gradle/gradle.properties && \
    echo "gpr.key=$(cat /run/secrets/gpr_token)" >> /root/.gradle/gradle.properties

# Switch to service folder where gradlew lives
WORKDIR /app/isajson-ena

# Ensure gradlew is executable
RUN chmod +x gradlew

# Build the JAR (skip tests and Spotless)
RUN ./gradlew build -x test -x spotlessApply --info --stacktrace \
    "-Dorg.gradle.jvmargs=--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"

# =========================
# Runtime stage: lightweight image
# =========================
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/isajson-ena/build/libs/*.jar app.jar

# Set entry point
ENTRYPOINT ["java", "-jar", "app.jar"]
