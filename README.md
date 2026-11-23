# WebSockets Lecture

A step-by-step demo for the lecture about WebSockets in a multi-instance Spring Boot setup:
one React UI talking to several copies of the same backend.

| Part       | Stack                                                                    |
|------------|--------------------------------------------------------------------------|
| `backend`  | Java 25, Spring Boot 4.1, Spring MVC, Spring Security (JWT), JPA, Flyway |
| `frontend` | React 19, TypeScript, Vite 8, Material UI 9                              |
| `deploy`   | Docker Compose: PostgreSQL, backend, nginx                               |

Each phase of the lecture is a git tag:

| Tag            | Phase                                                            |
|----------------|------------------------------------------------------------------|
| `step-1-http`  | Todo app over plain HTTP: login, public and private lists        |

## Phase 1 — todo app over HTTP

- Sign in as one of the demo users: `alice`, `bob`, `carol`. Password for all: `demo1234` (demo only).
- **Public** lists are visible to everyone, and anyone can add, tick or remove their items.
  **Private** lists are visible only to their owner. Only the owner can rename or delete a list.
- Plain HTTP: your own changes appear immediately, but other users' changes appear only after
  **Refresh**. Try it with Alice and Bob in two browser windows. That's the problem the next phase solves.

```
browser ──► nginx :8080 ──► /api/* ──► backend ──► PostgreSQL
```

Phase 1 runs a single backend. Several instances behind a load balancer come back in phase 3.

### API

| Method | Path                     | Body                       |
|--------|--------------------------|----------------------------|
| POST   | `/api/auth/login`        | `{username, password}`     |
| GET    | `/api/auth/me`           |                            |
| GET    | `/api/lists`             |                            |
| POST   | `/api/lists`             | `{title, visibility}`      |
| PATCH  | `/api/lists/{id}`        | `{title}`                  |
| DELETE | `/api/lists/{id}`        |                            |
| POST   | `/api/lists/{id}/items`  | `{title}`                  |
| PATCH  | `/api/items/{id}`        | `{title?, done?}`          |
| DELETE | `/api/items/{id}`        |                            |
| GET    | `/api/instance`          | (no login needed)          |

Everything except login and `/api/instance` needs `Authorization: Bearer <token>`.

## Run with Docker (recommended)

Requires Docker Desktop.

```bash
docker compose -f deploy/docker-compose.yml up --build
```

Open http://localhost:8080.

## Run locally (dev loop)

Requires JDK 25+, Node 22.12+ and Docker (for PostgreSQL).

```bash
# database only (exposed on localhost:5433)
docker compose -f deploy/docker-compose.yml up -d postgres

# backend (from backend/)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"

# frontend (from frontend/)
npm install
npm run dev        # http://localhost:5173
```

The Vite dev server proxies `/api` to `http://localhost:8081` (override with the `BACKEND_URL` env variable).

## Tests

Backend tests start a real PostgreSQL with Testcontainers, so **Docker must be running**.

```bash
(cd backend && ./mvnw test)
(cd frontend && npm run build && npm run lint)
```
