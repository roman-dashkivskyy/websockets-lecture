# WebSockets Lecture

A step-by-step demo for the lecture about WebSockets in a multi-instance Spring Boot setup:
one React UI talking to several copies of the same backend.

| Part       | Stack                                                                    |
|------------|--------------------------------------------------------------------------|
| `backend`  | Java 25, Spring Boot 4.1, Spring MVC, WebSocket, Spring AMQP, Spring Security (JWT), JPA, Flyway |
| `frontend` | React 19, TypeScript, Vite 8, Material UI 9                              |
| `deploy`   | Docker Compose: PostgreSQL, RabbitMQ, 3 backend instances, nginx load balancer |

Each phase of the lecture is a git tag:

| Tag                 | Phase                                                       |
|---------------------|-------------------------------------------------------------|
| `step-1-http`       | Todo app over plain HTTP: login, public and private lists   |
| `step-2-websockets` | Live updates: the server pushes every change over a WebSocket |
| `step-3-load-balancer` | Three instances behind a load balancer, and live updates break |
| `step-4-message-broker` | RabbitMQ fan-out: every instance hears every change, live updates work again |
| `step-5-short-messages` | Only ids go through the broker; each instance re-reads the data from Postgres |

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
  - (Phase 4 replaces `TodoNotifier` with `TodoEventPublisher` / `TodoEventListener` going through RabbitMQ;
    the registry methods became `notifyAllJson` / `notifyUserJson`.)

Known limitations, kept on purpose for the lecture:
- **The token sits in the URL.** Our nginx access log leaves out query strings, but the nginx error log (e.g. on
  a 502 while the backend is down) and any other proxy on the way may still record it. STOMP can send it in the
  CONNECT frame instead.
- **The token is checked only at the handshake.** An open socket keeps receiving updates after the JWT expires,
  but the next HTTP command gets 401 and signs the user out.
- **Concurrent edits can show a stale list.** Two people changing the same list at the same moment each push a
  snapshot with only their own change, and the last one wins until the next change. *Solved in phase 5:* only
  ids are sent, and the receiver re-reads the current state.
- **Pushes run on the HTTP request thread** after commit, so a very slow client can delay that response.
  (Since phase 4 they run on each instance's RabbitMQ listener thread instead.)

## Phase 3 — load balancer with three instances (and why it breaks)

Real systems run several copies of a service behind a load balancer (AWS ELB, GCP Load Balancing, …),
which may add or remove instances at any time. Here nginx round-robins over **Alpha, Bravo and Charlie**.

```
                      ┌─► backend-1 "Alpha"   ─┐
browser ──► nginx ────┼─► backend-2 "Bravo"   ─┼──► PostgreSQL
  /api/* round-robin  └─► backend-3 "Charlie" ─┘
  /i/N/api/* → backend-N only
```

**It breaks.** Every instance keeps its *own* in-memory list of open sockets (`SessionRegistry`). A change is
pushed only by the instance that handled the HTTP request, so browsers whose socket lives on another instance
never hear about it. `MultiInstanceTest` proves this with two real instances.

The UI shows what's going on:
- The **Backend** selector (header) sends everything through the load balancer or pins the tab to one instance.
- **HTTP answered by** shows which instance served the last request (`X-Instance-Name` header).
- **WebSocket: live on …** shows where the socket lives (the server's first message is `CONNECTED`).

Demo script:
1. Alice's tab on **Instance 1**, Bob's on **Instance 2**. Alice adds an item: Bob sees nothing.
2. Both on **Load balancer**: sometimes it works, sometimes not. Even Alice's own change may not come back,
   because her request went to Charlie while her socket lives on Alpha.
3. `docker compose -f deploy/docker-compose.yml stop backend-2`: Bob's chip shows "reconnecting". After
   `start backend-2` (or on the load balancer, right away on another instance) he reconnects and reloads the
   lists, so he catches up on what he missed.

**Why sticky sessions don't fix it.** Stickiness (e.g. nginx `ip_hash`) keeps *one client* on one instance. But
Alice and Bob are different clients on different instances, so Alice's change still never reaches Bob's
instance. Watch out in demos: on one laptop all tabs share an IP, so `ip_hash` would put everybody on the same
instance and hide the bug.

**Reconnect:** if the socket drops, the browser retries with exponential backoff (about 1 s, 2 s, 4 s … up to
15 s, with jitter) and reloads the lists after every reconnect.

The fix, a shared place where every instance publishes its events, is phase 4.

## Phase 4 — RabbitMQ carries the data

Every instance **publishes** its changes to RabbitMQ, and every instance **receives** all of them and pushes
them to its own sockets.

```
Alice ─HTTP─► Alpha ─publish─► [fanout exchange "todo.events"] ─► queue Alpha   ─► Alpha   ─WS─► Alice
                                                              ├─► queue Bravo   ─► Bravo   ─WS─► Bob
                                                              └─► queue Charlie ─► Charlie ─WS─► …
```

- **Fanout exchange:** a copy of every message goes to every bound queue. A single shared work queue would be
  the classic mistake: each message would reach only ONE instance.
- **One queue per instance:** exclusive and auto-delete, named like `todo.events.Bravo-…`. It disappears when
  the instance stops, and a new one appears when it starts.
- **Who receives it:** headers `audience` (`all` or `user`) and `username` say who should get it. The body is
  exactly the JSON the browser receives.
- **One path for everyone:** the instance that made the change gets it back from its own queue too.
- **The frontend didn't change at all.**
- **Backend pieces** (`realtime/`):
  - `BrokerConfig`: the exchange, this instance's queue and the binding between them.
  - `TodoEventPublisher`: publishes after commit.
  - `TodoEventListener`: receives from this instance's queue and pushes to its own sockets.
- **Test:** `MultiInstanceTest` now proves the fix with two real instances: a change made on Alpha reaches Bob
  on Bravo.

Demo script:
1. Alice on **Instance 1**, Bob on **Instance 2**. Alice adds an item and Bob sees it right away.
2. Both on **Load balancer**: changes always arrive.
3. Open the RabbitMQ UI at http://localhost:15672 (user `websockets`, password `websockets`, demo only).
   Show the exchange `todo.events`, the three queues (one per instance) and the message rates.
4. `docker compose -f deploy/docker-compose.yml stop backend-2`: Bravo's queue disappears. `start backend-2`:
   a new one appears.

Known limitations:
- **If RabbitMQ is down right after a commit, the event is lost.** It's logged, and the HTTP request still
  succeeds. The production fix is the *transactional outbox*: store events in the DB in the same transaction,
  publish them later.
- **No retries:** a message is acknowledged after it has been pushed to the sockets. If that fails, the event
  is dropped, not requeued. An instance that is down misses events; its browsers reload the lists after they
  reconnect.
- **Health:** when RabbitMQ is down, `/actuator/health` turns DOWN, but the readiness probe (used by the
  compose healthcheck) stays UP. HTTP still works, only live updates stop.
- **Messages still carry whole lists.** Phase 5 shows why that hurts and sends only ids.

## Phase 5 — only identifiers go through the broker

**The problem.** `PUT /api/lists/mine` replaces *all* of a user's lists at once, with any number of items. In
phase 4 the whole new content would travel through RabbitMQ, and the fanout exchange copies it into the queue of
**every** instance. Thousands of items × number of instances, for every such request: the broker becomes the
bottleneck. Brokers are built for many small messages, not a few huge ones.

**The fix: send a small "spec", not the data.** The message says only *which processor* should run (header
`processor`) and *with which ids* (JSON body). Each instance's processor re-reads the current data from Postgres
and pushes it to its own sockets.

```
Alice ─PUT─► Alpha ─► Postgres (commit)
               └─ publish ─► [fanout todo.events] ─► every instance's queue
                  processor = LISTS_REPLACED               │
                  {"owner":"alice","removed":[…],          ▼
                   "createdIds":[…]}           processor re-reads the new lists from Postgres
                                               → pushes them to THIS instance's sockets
```

| Processor        | Spec body                                      | What it does on each instance                     |
|------------------|------------------------------------------------|---------------------------------------------------|
| `LIST_CHANGED`   | `{"listId": 42}`                               | load list 42, push it (public → all, private → owner) |
| `LIST_DELETED`   | `{"listId": 42, "visibility": …, "owner": …}`  | push `LIST_DELETED` (the list can't be re-read)   |
| `LISTS_REPLACED` | `{"owner": …, "removed": […], "createdIds": […]}` | push the deletions, load the new lists in one query, push them |

- **Measured:** replacing 50 lists × 200 items (a 354 KB HTTP request) publishes a message of about
  **200 bytes**. Its size grows with the number of lists (their ids), never with the number of items.
  (188 bytes was measured for a user who had no lists before.)
- **The browser protocol didn't change**, so the frontend didn't either.
- **A side benefit:** receivers always read the current state, so concurrent edits can no longer leave a stale list
  on screen.
- **The trade-off:** every instance runs a database read per event, even instances where nobody who should
  receive it is connected. That's usually far cheaper than pushing big payloads through the broker, and the
  database already serves these reads for HTTP.
- **Known limitation:** two simultaneous replaces by the same user both survive: the user ends up with both new
  sets. Locking the user's row first (`SELECT … FOR UPDATE`) would serialize them.
- **Backend pieces** (`realtime/`):
  - `TodoEventPublisher`: publishes the spec after commit.
  - `TodoEventListener`: picks the processor named in the header; unknown ones are dropped.
  - `SpecProcessors`: the processors that re-read the data.
  - `SocketRouter`: public → all, private → the owner.

Demo script:
1. Bob's tab on **Instance 2**.
2. Run `node tools/replace-my-lists.mjs carol 50 200` (Node 22+). It replaces Carol's lists through the load
   balancer and prints the request size and the time taken. Use carol: replacing alice's lists would delete the
   seeded lists used by the earlier demos (until `docker compose … down -v`).
3. Bob's board updates: Carol's new public lists appear, her old ones disappear, and her private lists never show up.
4. To see the message sizes:
   - start the stack with `REALTIME_LOG_LEVEL=DEBUG`; each backend then logs lines like
     `Published LISTS_REPLACED spec (188 bytes)`;
   - the RabbitMQ UI shows the same tiny messages.

## Recap: where we started and where we ended

| Phase | What we added | What it taught |
|-------|---------------|----------------|
| 1 | A todo app over plain HTTP | Others see changes only after a refresh |
| 2 | WebSocket push (handshake, session registry, `notifyAll` / `notifyUser`) | Live updates, but only on one instance |
| 3 | Three instances behind a load balancer | Each instance pushes only to its own sockets: updates get lost |
| 4 | RabbitMQ fanout exchange, one queue per instance | Every instance hears every change |
| 5 | Specs with ids instead of whole objects | Keep broker messages small; re-read data where it's needed |

**In production you would also consider:**
- **STOMP over WebSocket with a broker relay.** You get user destinations instead of your own session registry,
  heartbeats, and the token in the CONNECT frame instead of the URL. We built the raw version on purpose, to see
  what happens underneath.
- **The transactional outbox.** It stores events in the same database transaction and publishes them afterwards,
  so a broker outage doesn't lose events.
- **Bulk inserts:** sequence-based ids with JDBC batching (`hibernate.jdbc.batch_size`) instead of IDENTITY
  (10,000 items are 10,000 round trips here), plus a cap on the request size.
- **Managed services:** a cloud load balancer, a managed RabbitMQ, or a hosted WebSocket gateway.

### API

| Method | Path                     | Body                       |
|--------|--------------------------|----------------------------|
| POST   | `/api/auth/login`        | `{username, password}`     |
| GET    | `/api/auth/me`           |                            |
| GET    | `/api/lists`             |                            |
| POST   | `/api/lists`             | `{title, visibility}`      |
| PUT    | `/api/lists/mine`        | `[{title, visibility, items: [{title, done}]}]` (replaces all my lists) |
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
# database and message broker only (localhost:5433, localhost:5672)
docker compose -f deploy/docker-compose.yml up -d postgres rabbitmq

# backends (one terminal each, from backend/)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --app.instance.name=Alpha"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --app.instance.name=Bravo"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --app.instance.name=Charlie"

# frontend (from frontend/)
npm install
npm run dev        # http://localhost:5173
```

The Vite dev server proxies `/i/N/api` (WebSocket included) to port `8080 + N` and `/api` to the first instance
(there is no real load balancer in dev mode; use Docker for that). Env variables: `BACKEND_BASE_PORT` (default
8081) and `VITE_BACKEND_COUNT` (default 3, also the number of instances in the UI's Backend selector).

## Tests

Backend tests start a real PostgreSQL and RabbitMQ with Testcontainers, so **Docker must be running**.

```bash
(cd backend && ./mvnw test)
(cd frontend && npm run build && npm run lint)
```
