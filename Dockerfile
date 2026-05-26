# Stage 1: Build file JAR bằng Maven
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Chạy ứng dụng với JRE nhẹ nhàng
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy file JAR từ stage 'build' qua nè
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]