# Stage 1: Build with Gradle Wrapper & JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime with JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
