# Property Repair Work Order System (物业报修工单系统)

## Overview

A complete backend system for property maintenance repair order management, replacing WeChat group-based repair requests with a structured, automated system. Built with Java 17, Spring Boot 3.2, and MySQL.

### Key Features

- **Auto-dispatch**: Automatically assigns repair orders to the best available worker based on skill match, current load, community proximity, and online status
- **Full lifecycle management**: Submit → Dispatch → Accept → Visit → Complete → Review/Rework
- **State machine**: Enforced valid transitions with audit trail
- **Timeout escalation**: Automatic escalation to supervisors when workers don't respond
- **Duplicate detection**: Identifies and merges duplicate submissions
- **Role-based access control**: Owner, Worker, Supervisor, Admin with JWT authentication
- **Audit logging**: Every state change is recorded with before/after snapshots

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (jjwt 0.12) |
| ORM | MyBatis Plus 3.5.6 |
| Database | MySQL 8.0 |
| Cache | Redis 7 |
| Build | Maven |
| API Docs | SpringDoc OpenAPI |
| Container | Docker + docker-compose |

---

## Project Structure

```
src/main/java/com/property/repair/
├── RepairSystemApplication.java          # Main entry point
├── config/                               # Configuration classes
│   ├── SecurityConfig.java               # Spring Security config
│   ├── RedisConfig.java                  # Redis template config
│   ├── MybatisPlusConfig.java            # Pagination + auto-fill
│   ├── SchedulerConfig.java              # Thread pool for scheduled tasks
│   └── RequireRoleAspect.java            # AOP for @RequireRole
├── common/
│   ├── Result.java                       # Unified API response
│   └── PageResult.java                   # Pagination wrapper
├── enums/                                # All enumerations
│   ├── UserRole.java                     # OWNER, WORKER, SUPERVISOR, ADMIN
│   ├── OrderStatus.java                  # PENDING → REVIEWED lifecycle
│   ├── ProblemType.java                  # PLUMBING, ELECTRICAL, etc.
│   ├── DispatchType.java                 # AUTO, MANUAL, TRANSFER, ESCALATION
│   ├── TimeoutType.java                  # ACCEPT, VISIT, COMPLETE
│   └── AttachmentStage.java              # SUBMIT, PROCESS, COMPLETE, REWORK
├── entity/                               # Database entity classes
│   ├── User.java, WorkerSkill.java
│   ├── RepairOrder.java                  # Core order entity
│   ├── DispatchRecord.java
│   ├── RepairProgress.java               # Timeline/audit trail
│   ├── Attachment.java, Review.java
│   ├── ReworkOrder.java
│   ├── TimeoutEscalation.java
│   └── AuditLog.java
├── dto/                                  # Request/Response DTOs
│   ├── RepairOrderSubmitRequest.java
│   ├── RepairOrderVO.java                # Detailed response with nested data
│   ├── TransferRequest.java, SuspendRequest.java
│   ├── CompleteRequest.java, ReviewRequest.java
│   ├── ReworkRequest.java, LoginRequest/Response.java
│   └── DispatchCandidate.java
├── mapper/                               # MyBatis Plus mapper interfaces
├── service/                              # Service interfaces
│   ├── RepairOrderService.java
│   ├── AttachmentService.java
│   ├── DispatchStrategy.java             # Pluggable dispatch interface
│   ├── AuditService.java
│   └── impl/                             # Service implementations
│       ├── RepairOrderServiceImpl.java   # Core business logic
│       ├── AttachmentServiceImpl.java
│       └── AuditServiceImpl.java
├── statemachine/
│   └── OrderStateMachine.java            # State transition validation
├── dispatch/
│   └── DefaultDispatchStrategy.java      # Weighted scoring dispatch
├── task/                                 # Scheduled tasks
│   ├── TimeoutEscalationTask.java        # Timeout detection + escalation
│   ├── WorkerStatusTask.java             # Online/offline heartbeat
│   └── CleanupTask.java                  # Stale record cleanup
├── controller/                           # REST API endpoints
│   ├── AuthController.java               # Login, logout, heartbeat
│   ├── RepairOrderController.java        # Order CRUD + lifecycle
│   ├── AttachmentController.java         # File upload/download
│   └── AdminController.java              # Admin operations
├── security/                             # Security infrastructure
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── SecurityUser.java
│   ├── SecurityUtils.java
│   └── RequireRole.java
└── exception/                            # Exception handling
    ├── BusinessException.java
    ├── InvalidStateTransitionException.java
    ├── DuplicateOrderException.java
    └── GlobalExceptionHandler.java
```

---

## Database Schema

### Tables

| Table | Purpose |
|-------|---------|
| `sys_user` | Users: owners, workers, supervisors, admins |
| `sys_worker_skill` | Worker skill-to-problem-type mapping (1-5 proficiency) |
| `sys_community` | Property communities |
| `sys_building` | Buildings within communities |
| `repair_order` | **Core**: repair work orders with full lifecycle state |
| `dispatch_record` | History of every dispatch/transfer/reassignment |
| `repair_progress` | Timeline of every state transition with operator info |
| `attachment` | File attachments (photos, videos, PDFs) per order |
| `review` | Owner reviews with 1-5 rating |
| `rework_order` | Rework requests after completion |
| `timeout_escalation` | Timeout events and escalation tracking |
| `audit_log` | Immutable audit trail with before/after JSON snapshots |

### ER Diagram (Simplified)

```
sys_user ──< repair_order (as owner)
sys_user ──< repair_order (as worker)
sys_user ──< sys_worker_skill

repair_order ──< dispatch_record
repair_order ──< repair_progress
repair_order ──< attachment
repair_order ──< review
repair_order ──< rework_order
repair_order ──< timeout_escalation
repair_order ──< audit_log
```

---

## State Machine

```
                    ┌──────────────────────────────────────────────────┐
                    │                                                  │
  PENDING ──────> DISPATCHED ──────> ACCEPTED ──────> VISITING ──────> COMPLETED ──────> REVIEWED
     │                │                  │                │                 │
     │                │ (reject)         │ (suspend)      │ (suspend)      │ (rework)
     │                ▼                  ▼                ▼                 ▼
     │             PENDING           SUSPENDED        SUSPENDED         REWORKING
     │                                  │                │                 │
     │                                  └──► ACCEPTED ◄──┘                │
     │                                  └──► VISITING ◄───────────────────┘
     │
     │                ┌─── TRANSFERRED ────> DISPATCHED (re-dispatch)
     │                │    (from ACCEPTED/VISITING)
     ▼                │
  CANCELLED ──────────┘

  Any non-terminal state ──> CLOSED (admin only)
```

### Valid Transitions Table

| From → To | Triggered By |
|-----------|-------------|
| PENDING → DISPATCHED | Auto-dispatch or manual assign |
| PENDING → CANCELLED | Owner cancel |
| DISPATCHED → ACCEPTED | Worker accept |
| DISPATCHED → PENDING | Worker reject (re-dispatch) |
| ACCEPTED → VISITING | Worker on-site |
| ACCEPTED → SUSPENDED | Worker suspend |
| ACCEPTED → TRANSFERRED | Worker transfer |
| VISITING → COMPLETED | Worker complete |
| VISITING → SUSPENDED | Worker suspend |
| VISITING → TRANSFERRED | Worker transfer |
| SUSPENDED → ACCEPTED/VISITING | Worker resume |
| TRANSFERRED → DISPATCHED | System re-dispatch |
| COMPLETED → REVIEWED | Owner review/confirm |
| COMPLETED → REWORKING | Owner rework request |
| REWORKING → VISITING | Worker re-visit |
| REWORKING → COMPLETED | Worker re-complete |

---

## Auto-Dispatch Strategy

The `DefaultDispatchStrategy` scores workers on a 0-100 composite:

| Factor | Weight | Logic |
|--------|--------|-------|
| **Skill match** | 40% | `proficiency / 5` for the problem type |
| **Current load** | 30% | `(MAX_LOAD - activeOrders) / MAX_LOAD` |
| **Community proximity** | 20% | 1.0 same building, 0.8 same community, 0.5 other |
| **Online status** | 10% | 1.0 online, 0.3 recently online (<30min), 0.0 offline |

### Fallback Chain

1. Skilled workers in the same community
2. Any worker in the same community
3. Any worker system-wide
4. If no workers at all → order stays PENDING for manual dispatch

---

## Scheduled Tasks

| Task | Frequency | Purpose |
|------|-----------|---------|
| Accept timeout check | Every 5 min | Escalate if worker hasn't accepted in 30 min |
| Visit timeout check | Every 5 min | Escalate if worker hasn't visited in 4 hours |
| Complete timeout check | Every 10 min | Escalate if repair not done in 48 hours |
| Second-level escalation | Every 1 hour | Escalate to admin if supervisor didn't handle |
| Worker heartbeat check | Every 2 min | Mark workers offline if no heartbeat for 10 min |
| Stale cleanup | Daily 3:00 AM | Clean up old escalation records |
| Stuck order detection | Every 30 min | Log warnings for orders stuck in intermediate states |

### Duplicate Trigger Prevention

- **Redis key per order+type**: `timeout:processed:{type}:{orderId}` with 24h TTL
- **Database check**: Verify no existing escalation record before creating
- **Idempotent**: Second run skips already-processed orders

---

## API Endpoints

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | Login with username/password |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Logout and invalidate token |
| POST | `/auth/heartbeat` | Worker heartbeat (keep online) |

### Repair Orders

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/orders` | OWNER | Submit new repair order |
| GET | `/orders` | ALL | Query orders (auto-filtered by role) |
| GET | `/orders/{id}` | ALL | Get order detail |
| POST | `/orders/{id}/accept` | WORKER | Accept assigned order |
| POST | `/orders/{id}/reject` | WORKER | Reject (triggers re-dispatch) |
| POST | `/orders/{id}/transfer` | WORKER | Transfer to another worker |
| POST | `/orders/{id}/visit` | WORKER | Record on-site visit |
| POST | `/orders/{id}/suspend` | WORKER | Suspend order |
| POST | `/orders/{id}/resume` | WORKER | Resume suspended order |
| POST | `/orders/{id}/complete` | WORKER | Mark repair completed |
| POST | `/orders/{id}/review` | OWNER | Review with rating |
| POST | `/orders/{id}/rework` | OWNER | Request rework |
| POST | `/orders/{id}/confirm` | OWNER | Confirm completion |
| POST | `/orders/{id}/cancel` | OWNER | Cancel pending order |
| POST | `/orders/{id}/dispatch` | ADMIN/SUPERVISOR | Manual dispatch |
| POST | `/orders/{id}/close` | ADMIN | Force close order |
| POST | `/orders/{dup}/merge/{parent}` | ADMIN/SUPERVISOR | Merge duplicate |

### Attachments

| Method | Path | Description |
|--------|------|-------------|
| POST | `/attachments/upload` | Upload file |
| GET | `/attachments/order/{id}` | Get order attachments |
| GET | `/attachments/order/{id}/stage/{stage}` | Get by stage |
| DELETE | `/attachments/{id}` | Delete attachment |

### Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/dispatch/candidates/{id}` | Preview dispatch candidates |
| GET | `/admin/escalations` | Get unhandled escalations |
| POST | `/admin/escalations/{id}/handle` | Handle an escalation |

---

## Edge Cases Handled

| Problem | Solution |
|---------|----------|
| **Duplicate submissions** | Detects same owner+community+building+type within 24h window; logs warning, allows merge |
| **Worker offline** | Online status weighted in dispatch score; fallback to next best worker |
| **Transfer state inconsistency** | Redis distributed lock on dispatch; explicit TRANSFERRED intermediate state |
| **Rework after completion** | COMPLETED → REWORKING → VISITING → COMPLETED cycle; tracked via rework_order table |
| **Missing attachments** | Attachments linked by stage (SUBMIT/PROCESS/COMPLETE/REWORK); validation at completion |
| **Timeout duplicate triggers** | Redis key + DB check dual prevention; 24h TTL on processed markers |
| **Concurrent dispatch** | Redis `setIfAbsent` lock with 10s TTL per order |
| **Stuck orders** | Periodic detection task logs warnings for orders in TRANSFERRED > 1h |

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for MySQL + Redis)

### 1. Start Infrastructure

```bash
docker-compose up -d mysql redis
```

### 2. Initialize Database

The `docker-compose.yml` automatically runs `schema.sql` and `data.sql` on first start.

Default test accounts (password: `123456`):

| Username | Role | Description |
|----------|------|-------------|
| admin | ADMIN | System administrator |
| supervisor1 | SUPERVISOR | Community 1 supervisor |
| worker1 | WORKER | Plumbing expert (community 1) |
| worker2 | WORKER | Electrical specialist (community 1) |
| owner1 | OWNER | Test owner (community 1) |

### 3. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/repair-system-1.0.0.jar
```

### 4. Access API

```
http://localhost:8080/api/swagger-ui.html
```

### 5. Full Stack (with Docker)

```bash
mvn clean package -DskipTests
docker-compose up -d
```

### 6. Run Tests

```bash
mvn test
```

---

## Configuration

Key settings in `application.yml`:

```yaml
jwt:
  secret: your-secret-key          # JWT signing key
  expiration: 86400000              # Access token TTL (24h)
  refresh-expiration: 604800000    # Refresh token TTL (7 days)

repair:
  timeout:
    accept-minutes: 30             # Worker must accept within 30 min
    complete-hours: 48             # Must complete within 48 hours
    escalation-delay: 15           # Escalate 15 min after deadline
  duplicate:
    window-hours: 24               # Lookback window for duplicates
  upload:
    base-path: ./uploads           # File upload directory
    allowed-types: jpg,jpeg,png,gif,mp4,avi,pdf
    max-size: 10485760             # 10MB per file
```

---

## License

MIT
