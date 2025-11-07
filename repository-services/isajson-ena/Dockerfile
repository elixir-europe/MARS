# Builder stage
FROM gradle:jdk11 AS builder

WORKDIR /app

# Copy everything from context (service folder)
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build the JAR while mounting GitHub secrets
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    mkdir -p /root/.gradle && \
    echo "gpr.user=$(cat /run/secrets/gpr_user)" >> /root/.gradle/gradle.properties && \
    echo "gpr.token=$(cat /run/secrets/gpr_token)" >> /root/.gradle/gradle.properties && \
    ./gradlew build -x test -x spotlessApply --info --stacktrace \
      "-Dorg.gradle.jvmargs=--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
       --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
       --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
       --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
       --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"

# Runtime stage
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
