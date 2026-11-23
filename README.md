# WebSockets Lecture

A step-by-step demo for the lecture about WebSockets in a multi-instance Spring Boot setup:
one React UI talking to several copies of the same backend.

| Part       | Stack                                                                    |
|------------|--------------------------------------------------------------------------|
| `backend`  | Java 25, Spring Boot 4.1, Spring MVC, WebSocket, Spring Security (JWT), JPA, Flyway |
| `frontend` | React 19, TypeScript, Vite 8, Material UI 9                              |
| `deploy`   | Docker Compose: PostgreSQL, backend, nginx                               |

Each phase of the lecture is a git tag:

| Tag                 | Phase                                                       |
|---------------------|-------------------------------------------------------------|
| `step-1-http`       | Todo app over plain HTTP: login, public and private lists   |
| `step-2-websockets` | Live updates: the server pushes every change over a WebSocket |

## Phase 1 — todo app over HTTP

- Sign in as one of the demo users: `alice`, `bob`, `carol`. Password for all: `demo1234` (demo only).
- **Public** lists are visible to everyone, and anyone can add, tick or remove their items.
  **Private** lists are visible only to their owner. Only the owner can rename or delete a list.
- Plain HTTP: your own changes appear immediately, but other users' changes appear only after
  **Refresh**. That's the problem phase 2 solves.

## Phase 2 — live updates over WebSockets

**HTTP for commands, WebSocket for updates.** The page loads the lists once over HTTP and then opens
`ws://<host>/api/ws?token=...`. Every change (yours or anybody else's) comes back as a push message, and the
page updates itself. Try it with Alice and Bob in two browser windows: no Refresh needed.

```
browser ──HTTP: change──►  nginx :8080 ──► backend ──► PostgreSQL
   ▲                                          │ after commit
   └────────────── WebSocket: push ◄──────────┘
```

- **Handshake:** a normal HTTP `GET` with `Upgrade: websocket`, answered with `101 Switching Protocols`
  (see it in DevTools → Network → WS). The browser can't send an `Authorization` header here, so the JWT goes
  in the URL and `TokenHandshakeInterceptor` checks it. Only pages from the same origin may connect.
- **Messages:** `{"type": "LIST_CHANGED", "list": {...the whole list...}}` or `{"type": "LIST_DELETED", "listId": 42}`.
  Public lists go to everyone, private lists only to all open tabs of their owner.
- **Backend pieces** (`realtime/`):
  - `WebSocketConfig` registers the endpoint.
  - `TokenHandshakeInterceptor` checks the user.
  - `TodoSocketHandler` handles the connection lifecycle.
  - `SessionRegistry` holds username → open sessions, with `notifyAll` / `notifyUser`.
  - `TodoNotifier` sends after the transaction commits.

Known limitations, kept on purpose for the lecture:
- **The token sits in the URL.** Our nginx access log leaves out query strings, but the nginx error log (e.g. on
  a 502 while the backend is down) and any other proxy on the way may still record it. STOMP can send it in the
  CONNECT frame instead.
- **The token is checked only at the handshake.** An open socket keeps receiving updates after the JWT expires,
  but the next HTTP command gets 401 and signs the user out.
- **Concurrent edits can show a stale list.** Two people changing the same list at the same moment each push a
  snapshot with only their own change, and the last one wins until the next change. Phase 5 sends ids instead,
  and the receiver re-reads the current state.
- **Pushes run on the HTTP request thread** after commit, so a very slow client can delay that response.

Phases 1–2 run a single backend. Several instances behind a load balancer come in phase 3.

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
| GET    | `/api/ws?token=<jwt>`    | WebSocket handshake        |
| GET    | `/api/instance`          | (no login needed)          |

Everything except login, `/api/instance` and the WebSocket handshake needs `Authorization: Bearer <token>`.

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

The Vite dev server proxies `/api` (including the WebSocket) to `http://localhost:8081` (override with the `BACKEND_URL` env variable).

## Tests

Backend tests start a real PostgreSQL with Testcontainers, so **Docker must be running**.

```bash
(cd backend && ./mvnw test)
(cd frontend && npm run build && npm run lint)
```
