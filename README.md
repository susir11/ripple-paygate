# Ripple Paygate

Ripple Paygate is a beginner microservices practice project built using Spring Boot and Spring Cloud.

The project currently contains:

- API Gateway
- Eureka Server
- Payment Service

The API Gateway handles incoming requests and routes them to the Payment Service using Eureka Service Discovery.

The Payment Service accepts payment data as JSON and stores it in a PostgreSQL database using Spring Data JPA.

## Tech Stack

- Java 21
- Spring Boot
- Spring Cloud Gateway
- Netflix Eureka
- PostgreSQL
- Maven

## Services

| Service | Port |
|---|---|
| API Gateway | 8080 |
| Payment Service | 9091 |
| Eureka Server | 8761 |

## Current Flow

```text
Client Request
      ↓
API Gateway
      ↓
Payment Service
      ↓
PostgreSQL Database
