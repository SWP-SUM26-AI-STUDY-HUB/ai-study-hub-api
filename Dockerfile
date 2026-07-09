# Stage 1: Build JAR bằng Maven trên JDK 21 (virtual threads cần Java 21+)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Stage 2: Chạy ứng dụng với JRE nhẹ nhàng
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy file JAR từ stage 'build' qua nè
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]