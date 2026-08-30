# NEZAFI

## Commercial Rental Management Platform

NEZAFI is a web-based management application designed to centralize and simplify the management of commercial rental properties, tenants, contracts, documents and administrative operations.

The project is being developed in a real-world business context for the management of a commercial property portfolio.

---

## 🎯 Project Overview

NEZAFI aims to provide a centralized platform for managing the operational and administrative aspects of commercial rental activities.

The application brings together several workflows, including:

- Commercial location management
- Tenant management
- Rental contract management
- Contract status tracking
- Document and invoice management
- Personnel and role management
- Secretariat operations
- Access control and security

The project is designed to evolve toward deployment on a company's local network (LAN), allowing multiple workstations to access a centralized application and database.

---

## ✨ Main Features

### 🔐 Authentication & Security

- User authentication
- Role-based access control
- Protected application resources
- Authorization checks for sensitive operations
- Protection against unauthorized resource manipulation
- Password management
- Security hardening and access-control improvements

### 🏢 Commercial Locations

- Management of commercial locations
- Availability and operational status tracking
- Association between locations and rental contracts
- Organization of the commercial property inventory

### 📄 Rental Contracts

- Contract creation and management
- Contract status management
- Contract expiration tracking
- Contract termination workflows
- Contract preview
- Contract filtering and sorting
- Access restrictions depending on contract state

### 📁 Documents & Invoices

- Contract document management
- Invoice and scanned document support
- Document upload
- Inline document preview
- Association of documents with contracts

### 👥 Personnel & Roles

- Personnel management
- Role-based permissions
- Separation of administrative responsibilities
- Access control according to user role

### 🏛️ Secretariat

The secretariat module provides administrative workflows for the day-to-day management of rental information, contracts, documents and related operations.

---

## 🏗️ Architecture

NEZAFI currently follows a server-side web application architecture based on Spring Boot.


                    ┌──────────────────────┐
                    │      Web Browser     │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │     Spring Boot      │
                    │       Backend        │
                    ├──────────────────────┤
                    │ Controllers          │
                    │ Services             │
                    │ Security             │
                    │ Business Logic       │
                    │ Persistence          │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │      Database        │
                    │   H2 → PostgreSQL    │
                    └──────────────────────┘

## 🛠️ Technology Stack

| Technology | Role |
|---|---|
| Java 21 | Programming language |
| Spring Boot | Backend framework |
| Spring Security | Authentication and authorization |
| Thymeleaf | Server-side web interface |
| Spring Data JPA | Data persistence |
| H2 | Development database |
| PostgreSQL | Target production database |
| Flyway | Database migrations |
| HTML / CSS | Web interface |
| Maven | Build and dependency management |
| Git / GitHub | Version control |

---

## 🔒 Security

Security is an important aspect of NEZAFI.

The project includes ongoing work around:

- Authentication
- Role-based authorization
- Resource-level access control
- Protection against unauthorized operations
- Secure contract workflows
- Restricted deletion operations
- Password management
- Security auditing and hardening

Security-related development is continuously refined as the application evolves.

---

## 🗄️ Database

The project initially uses H2 for development.

A migration toward PostgreSQL is currently being implemented to provide a more appropriate production database environment.

Database schema evolution is managed using Flyway migrations.


Development
     │
     ▼
    H2
     │
     │ Migration
     ▼
PostgreSQL


## 🌐 Deployment

NEZAFI is designed to support deployment within a company's local area network (LAN).

A target deployment architecture is:

```text
                    Company LAN
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
   Secretariat PC    Office PC 1    Office PC 2
          │              │              │
          └──────────────┼──────────────┘
                         │
                         ▼
                  NEZAFI Server
                         │
                         ▼
                     PostgreSQL
```

The objective is to allow multiple workstations to access the application through the company's internal network while keeping the application and database under local control.

The deployment strategy is intended to provide:

- Centralized application access
- Centralized database management
- Local network accessibility
- Reduced dependency on external hosting
- Local database backup and recovery
- Controlled access to the application within the organization

---

## 📈 Project Status

🚧 **Active Development**

NEZAFI is currently under active development and progressive refinement.

### Current development areas

- PostgreSQL migration
- Database migration management with Flyway
- LAN deployment
- Database backup and recovery strategy
- Security hardening
- Functional refinement
- Production readiness

The application is progressively moving from a development environment toward a stable deployment suitable for real-world organizational use.

## 📄 License

This repository does not currently use an open-source license.

The source code is maintained for professional and development purposes. Reuse, redistribution, or commercial use of the code requires prior authorization from the project owners.

All rights reserved.
