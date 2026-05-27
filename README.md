# AI Study Hub API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?style=for-the-badge&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue?style=for-the-badge&logo=docker)](https://www.docker.com/)

**AI Study Hub API** is a powerful and modern Backend API system built on the **Spring Boot 4.0.6** ecosystem and **Java 17**. The project provides a smart learning document storage solution integrated with an AI virtual assistant to interact directly with document content, manage storage plans, invoices, and an advanced content moderation system.

---

## Key Features

### 1. Security & Authentication
- **Dual-Token JWT System**: Supports short-lived Access Tokens (15 minutes) and long-lived Refresh Tokens (7 days).
- **Redis-backed Token Rotation & Blacklist**: Automatically rotates Refresh Tokens to enhance security and stores a Blacklist in Redis when a user logs out.
- **Spring Security**: Strict role-based authorization between `User` and `Admin`.
- **Google OAuth2**: Supports quick login via Google accounts.

### 2. Smart Document Management
- **Upload & Document Processing**: Supports multiple formats with real-time status updates (`uploading`, `processing`, `pending`, `private`, `public`, `failed`).
- **Tag-based Categorization**: Smart tagging helps users easily search and organize their study materials.
- **Document Sharing**: A flexible document sharing mechanism via secure unique URLs.

### 3. AI Study Assistant
- **Context-aware Document Chat**: Users can create chat sessions (`chat_sessions`) linked to one or more study documents (`session_documents`).
- **Accurate Responses with Citations**: The AI responds to questions directly based on the knowledge from selected documents and provides specific source citations as a JSONB structure.

### 4. Storage Plans & Billing
- **Subscription-based Plans**: A flexible configuration system for storage plans (`storage_plans`) that includes maximum storage limits and daily AI request quotas.
- **Invoicing System**: Integrated clear transaction history with payment statuses (`pending`, `success`, `failed`).

### 5. Moderation & Community
- **Reviews & Ratings**: Allows users to rate documents with a score along with detailed comments.
- **Report System**: Users can report violating documents. Administrators (Admin) have moderation tools and can log violation history (`violation_histories`).
- **Limit Management**: Automatically transitions user status to `overlimitstorage` when they exceed their plan limits.

---

## Tech Stack & Technologies

- **Core Framework**: Spring Boot 4.0.6, Spring WebMVC, Spring Data JPA, Spring Security
- **Language**: Java 17
- **Database**: PostgreSQL 16 (Supports JSONB, Enums, and Auto Identity)
- **Cache & Session**: Redis 7 (Stores and rotates Refresh Tokens, manages logout blacklist)
- **API Documentation**: Springdoc OpenAPI / Swagger UI 3.0.2
- **Utilities**: Project Lombok (Automatically generates boilerplate code)
- **Containerization**: Docker & Docker Compose

---

## Project Directory Structure

```text
ai-study-hub-api/
├── .github/                  # CI/CD automation workflows
├── src/
│   ├── main/
│   │   ├── java/vn/ai_study_hub_api/
│   │   │   ├── common/       # Global shared objects (ApiResponse, ...)
│   │   │   ├── config/       # OpenApi, WebMvc, CORS configurations...
│   │   │   ├── controller/   # Controller layer handling RESTful APIs and Request/Response DTOs
│   │   │   ├── exception/    # Centralized exception handling (Global Exception Handler & AppException)
│   │   │   ├── model/        # JPA Database Entities (UserEntity, UserRole, UserStatus, ...)
│   │   │   ├── repository/   # Database interaction layer (Spring Data JPA Repositories)
│   │   │   ├── security/     # Authorization, JWT Filter, Token Provider, Custom User Details
│   │   │   ├── service/      # Contains interfaces and business logic implementations (Auth, User, Redis...)
│   │   │   └── AiStudyHubApiApplication.java # Application main runner class
│   │   └── resources/
│   │       ├── application.yaml       # Global system configuration
│   │       ├── application-dev.yaml   # Development environment configuration
│   │       ├── application-prod.yaml  # Production environment configuration
│   │       ├── templates/
│   │       └── static/
│   └── test/                 # Contains Unit Tests & Integration Tests (Services & Controllers)
├── Dockerfile                # Configures packaging the application into a Docker Image (Multi-stage build)
├── docker-compose.yaml       # Defines Docker run environments (PostgreSQL, Redis, API Service)
├── initdb.sql                # Initial database schema setup file for PostgreSQL
├── .env                      # Contains sensitive environment variables (should not be committed to Git)
├── pom.xml                   # Manages dependencies and Maven build configuration
└── README.md                 # Project documentation guide (This file)
```

---

## Installation & Quick Start Guide

### 1. Environment Prerequisites
- **Docker** & **Docker Compose** installed.
- **Java 17** and **Maven** installed (if you wish to run locally without Docker).

### 2. Configure Environment Variables
Create a `.env` file at the root directory of the project (template available in the sample `.env` file):
```env
# Database Config
DATABASE_URL=jdbc:postgresql://postgres:5432/postgres
DATABASE_USERNAME=nnct
DATABASE_PASSWORD=YourSecurePasswordHere

# Redis Config
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=YourSecureRedisPasswordHere

# JWT Config
JWT_SECRET=YourSuperLongAndSecureJwtSecretKeyHere
```

### 3. Run the entire application using Docker Compose (Recommended)
At the root directory of the project, run the following command to automatically pull and launch the database, Redis, and Backend API service:
```bash
docker-compose up --build -d
```
This command will:
- Spin up **PostgreSQL** on port `5432` and automatically import the `initdb.sql` file.
- Spin up **Redis** on port `6379`.
- Build the Spring Boot project into a JAR file (Multi-stage) and run it on port `8080`.

### 4. Run locally for Development
If you want to run Spring Boot locally for quick debugging:
1. Ensure you have started the database and Redis services via Docker:
   ```bash
   docker-compose up -d postgres redis
   ```
2. Run the Spring Boot application using the Maven wrapper:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

---

## API Documentation & Swagger UI

The project is fully integrated with **Swagger OpenAPI 3** API visualization tool.

Once the application is running successfully, you can access the following link to view detailed endpoint lists and try sending requests directly on the interface:

**Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### Basic API Workflow (JWT Authentication Example)

1. **Login**:
   - **Method**: `POST`
   - **Path**: `/api/v1/auth/login`
   - **Body (JSON)**:
     ```json
     {
       "email": "user@aistudyhub.vn",
       "password": "yourpassword"
     }
     ```
   - **Response**: Returns user account information and an `accessToken` & `refreshToken` pair.

2. **Refresh Token**:
   - **Method**: `POST`
   - **Path**: `/api/v1/auth/refresh`
   - **Body (JSON)**:
     ```json
     {
       "refreshToken": "your-refresh-token-received-earlier"
     }
     ```
   - **Response**: Returns a new `accessToken` and a rotated `refreshToken`.

3. **Logout**:
   - **Method**: `POST`
   - **Path**: `/api/v1/auth/logout`
   - **Headers**: `Authorization: Bearer <your_access_token>`
   - **Response**: Revokes the active session, deletes the Refresh Token in Redis, and blacklists the current Access Token to invalidate it immediately.

---

## Running Tests

The project comes pre-configured with a suite of unit tests for core services (such as `UserService`, `AuthService`). To run the entire test suite, execute:
```bash
./mvnw clean test
```

---

## Contribution Guide

1. **Fork** this project to your personal account.
2. Create a new branch matching your feature development (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push your branch to the Remote Repository (`git push origin feature/AmazingFeature`).
5. Create a **Pull Request** for the administrator to review and merge your code.