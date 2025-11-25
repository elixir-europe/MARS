# Builder stage
FROM gradle:jdk11 AS builder

WORKDIR /app

# Copy everything from context (the service folder)
COPY . .

ARG GPR_USER
ARG GPR_TOKEN

# Make gradlew executable
RUN chmod +x gradlew
# Build the JAR
RUN mkdir -p /root/.gradle && \
    echo "gpr.user=${GPR_USER}" >> /root/.gradle/gradle.properties && \
    echo "gpr.token=${GPR_TOKEN}" >> /root/.gradle/gradle.properties && \
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
ARG JAR_FILE=/app/isajson-biosamples/build/libs/*.jar
COPY --from=builder ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]


