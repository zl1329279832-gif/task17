# Property Repair Management System

## Overview

A complete backend system for property management companies to handle repair requests digitally, replacing the informal WeChat group-based workflow.

## Tech Stack

- **Java 17** / **Spring Boot 3.2.x**
- **Spring Security** + **JWT** authentication
- **MyBatis Plus** ORM
- **MySQL** database
- **Redis** for caching, distributed locks, and idempotency
- **Spring @Scheduled** for timed tasks

## Features

### Core Workflow
- Owners submit repair requests with photos/videos
- System auto-dispatches to the best-matched repair worker
- Workers manage the full lifecycle: accept, start, suspend, resume, complete
- Owners confirm completion, rate the service, or request rework
- Timeout escalation to supervisors when workers don't respond

### Smart Auto-Dispatch
Scoring algorithm considers:
| Factor | Weight | Description |
|--------|--------|-------------|
| Community match | Required | Worker must serve the community |
| Skill match | 30 pts | Based on proficiency level (1-3) |
| Workload | 30 pts | Fewer active orders = higher score |
| Proximity | 25 pts | Building distance calculation |
| Online status | 15 pts | Online workers preferred |

### State Machine
```
PENDING -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED -> CONFIRMED -> EVALUATED
                                       |  SUSPENDED
          ESCALATED <-(timeout)
          REWORK <-(rework request)     REJECTED <-(owner rejects)
```

### Role-Based Access Control
| Role | Permissions |
|------|------------|
| OWNER | Submit repairs, confirm completion, rate, request rework |
| WORKER | Accept, start, suspend, resume, complete repairs |
| SUPERVISOR | Handle escalations, reassign workers |
| ADMIN | Full system management |

### Additional Features
- **Duplicate detection**: Same community + building + category within 48 hours
- **Idempotency**: Redis-based request deduplication via `X-Idempotent-Key` header
- **Optimistic locking**: Prevents concurrent state conflicts
- **Distributed locks**: Prevents duplicate scheduled task execution
- **Audit logging**: AOP-based operation tracking
- **File attachments**: Upload photos for reports, progress, and completion

## Project Structure

```
src/main/java/com/property/repair/
├── PropertyRepairApplication.java
├── common/          # Enums, constants, exceptions, Result wrapper, annotations, AOP
├── config/          # MyBatis Plus, Redis, WebMvc, CORS, file upload configs
├── security/        # Spring Security, JWT, filters, LoginUser
├── entity/          # 13 entity classes + BaseEntity
├── dto/             # Request/Response DTOs
├── mapper/          # MyBatis Plus mapper interfaces
├── service/         # Business logic (interfaces + implementations)
├── controller/      # REST controllers
├── statemachine/    # Order state machine (lightweight custom implementation)
├── strategy/        # Dispatch strategy pattern (auto/manual)
└── scheduler/       # Scheduled tasks (timeout detection, reminders)
```

## Database Schema

13 tables in MySQL:

| Table | Description |
|-------|-------------|
| sys_user | Users (owners, workers, supervisors, admins) |
| community | Residential communities |
| building | Buildings with coordinates |
| category | Repair categories (2-level hierarchy) |
| worker_skill | Worker skills per community and category |
| repair_order | Main repair order table |
| dispatch_record | Dispatch history |
| repair_progress | Status change timeline |
| attachment | File attachments |
| evaluation | Owner ratings |
| rework_record | Rework history |
| timeout_escalation | Timeout escalation records |
| audit_log | Operation audit trail |

## API Endpoints

### Authentication
```
POST   /api/auth/login          # Login
POST   /api/auth/refresh        # Refresh token
POST   /api/auth/logout         # Logout
GET    /api/auth/me             # Current user info
```

### Repair Orders
```
POST   /api/repair-orders                    # Create repair order
GET    /api/repair-orders                    # List (paginated, role-filtered)
GET    /api/repair-orders/{id}               # Detail
POST   /api/repair-orders/{id}/accept        # Worker accepts
POST   /api/repair-orders/{id}/start         # Start repair
POST   /api/repair-orders/{id}/suspend       # Suspend (with reason)
POST   /api/repair-orders/{id}/resume        # Resume
POST   /api/repair-orders/{id}/complete      # Complete
POST   /api/repair-orders/{id}/confirm       # Owner confirms
POST   /api/repair-orders/{id}/reject        # Owner rejects
POST   /api/repair-orders/{id}/rework        # Request rework
POST   /api/repair-orders/{id}/evaluate      # Rate service
GET    /api/repair-orders/{id}/timeline      # Progress timeline
POST   /api/repair-orders/duplicate-check    # Check duplicates
```

### Dispatch & Escalation
```
GET    /api/dispatch/candidates/{orderId}    # Candidate workers
POST   /api/dispatch/manual                  # Manual dispatch
POST   /api/dispatch/reassign                # Reassign worker
GET    /api/escalations                      # Pending escalations
POST   /api/escalations/{id}/resolve         # Resolve escalation
```

### Attachments, Evaluations, Admin
```
POST   /api/attachments/upload               # Upload file
GET    /api/attachments/{id}                  # Download file
GET    /api/communities                      # Community list
GET    /api/categories                       # Category list
GET    /api/dashboard/stats                  # Dashboard statistics
GET    /api/admin/users                      # User management
GET    /api/admin/audit-logs                 # Audit logs
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### Setup

1. **Create MySQL database**:
```sql
CREATE DATABASE property_repair DEFAULT CHARACTER SET utf8mb4;
```

2. **Initialize tables and test data**:
```bash
mysql -u root -p property_repair < src/main/resources/db/schema.sql
```

3. **Configure** `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/property_repair
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

4. **Build and run**:
```bash
mvn clean package -DskipTests
java -jar target/property-repair-1.0.0-SNAPSHOT.jar
```

5. **Test login** (default admin account):
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Running Tests
```bash
mvn test
```

## Configuration

Key configuration in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `jwt.secret` | (auto) | JWT signing secret (min 32 chars) |
| `jwt.access-token-expiration` | 3600000 | Access token TTL (ms) |
| `jwt.refresh-token-expiration` | 604800000 | Refresh token TTL (ms) |
| `repair.accept-timeout-minutes` | 30 | Auto-escalation threshold |
| `repair.repair-timeout-hours` | 24 | Repair timeout reminder |
| `repair.duplicate-detect-hours` | 48 | Duplicate detection window |
| `file.upload-dir` | ./uploads | File storage directory |
| `file.max-size` | 10MB | Max upload file size |
