# Ripple PayGate

Ripple PayGate is a microservices-based payment gateway built with Java Spring Boot.
It simulates a real-world payment processing system with authentication, service discovery,
fault tolerance, and a frontend interface — all running inside Docker containers.

---

## What It Does

A user opens the frontend, registers or logs in, and submits a payment. The payment
request travels through the API Gateway, which checks the user's identity before
forwarding it to the Payment Service. The Payment Service saves the transaction to
a PostgreSQL database and returns a confirmation with a unique transaction ID.

If the Payment Service goes down, the Circuit Breaker detects the failure and
immediately returns a clean error message instead of leaving the user waiting.

---

## Services

### Eureka Server (Port 8761)
The service registry. Every microservice registers itself here when it starts up.
The API Gateway uses Eureka to discover where to send requests — no hardcoded URLs
between services.

### API Gateway (Port 8080)
The single entry point for all requests. No request reaches any other service
without going through here first. It does three things:
- **JWT Validation** — checks that every payment request carries a valid login token
- **Routing** — forwards `/auth/**` to Auth Service and `/api/payments/**` to Payment Service
- **Circuit Breaker** — monitors Payment Service health and returns a fallback response if it goes down

### Auth Service (Port 8082)
Handles user registration and login. When a user logs in successfully, it generates
a JWT token — a signed credential the user must send with every payment request.
Passwords are never stored in plain text; they are encrypted with BCrypt before saving.

### Payment Service (Port 9091)
Processes payment requests. Receives the payment amount, generates a unique transaction ID,
sets the status to SUCCESS, and saves the record to the PostgreSQL database.
Only reachable through the API Gateway — direct access is blocked.

### PostgreSQL Database (Port 5433)
Stores all application data in two tables:
- `users` — registered accounts (managed by Auth Service)
- `payments` — processed transactions (managed by Payment Service)

### Frontend (Port 3000)
A simple web interface served by Nginx. Users can register, log in, and submit payments
without needing Postman or any technical tools.

---

## Architecture
Browser (localhost:3000)

↓

API Gateway (localhost:8080)

├── JWT Validation

├── Circuit Breaker

↓                    ↓

Auth Service        Payment Service

(register/login)    (process payment)

↓                    ↓

PostgreSQL Database


---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot | Application framework |
| Spring Cloud Gateway | Reactive API Gateway |
| Netflix Eureka | Service discovery |
| Spring Security + JWT | Authentication |
| Resilience4j | Circuit Breaker |
| Spring Data JPA | Database access |
| PostgreSQL | Persistent storage |
| Docker + Docker Compose | Containerization |
| Nginx | Frontend static file server |

---

## How to Run

### Prerequisites
- Docker Desktop installed and running
- Git

### Steps

1. Clone the repository: git clone https://github.com/susir11/ripple-paygate.git
cd ripple-paygate

2. Create a `.env` file at the project root:
DB_NAME=paygate_db
DB_USERNAME=postgres
DB_PASSWORD=your_password_here
JWT_SECRET=your_jwt_secret_at_least_32_characters_long

3. Build all service JARs (run from each service folder):
cd eureka-server && .\mvnw.cmd clean package -DskipTests && cd ..
cd auth-service && .\mvnw.cmd clean package -DskipTests && cd ..
cd api-gateway && .\mvnw.cmd clean package -DskipTests && cd ..
cd payment-service && .\mvnw.cmd clean package -DskipTests && cd ..

4. Start the full stack:
   docker compose up --build

5. Open the frontend: `http://localhost:3000`

## API Endpoints

All requests go through the API Gateway on port 8080.

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| POST | /auth/register | No | Create a new account |
| POST | /auth/login | No | Login and receive JWT token |
| POST | /api/payments/process | Yes (Bearer token) | Process a payment |

---

## Monitoring

- **Eureka Dashboard** — `http://localhost:8761` — shows all registered services
- **Circuit Breaker Health** — `http://localhost:8080/actuator/health` — shows circuit breaker state (CLOSED / OPEN / HALF_OPEN)

