# Lyaba Auction App — Backend Specification

Derived from full analysis of the Android client (models, repositories, ViewModels, WebSocket manager, API service). This document is the single source of truth for building the server and database.

---

## 1. Overview

Real-time soccer player auction server. 20+ users join a room, bid on players in sequence, 10-second countdown resets on every new bid, highest bidder wins and budget is deducted. Server is authoritative for all bids and timer state.

---

## 2. Recommended Tech Stack

| Concern | Choice |
|---|---|
| Runtime | Node.js 20+ |
| Framework | Express.js |
| WebSocket | `ws` library (raw, matches OkHttp client) |
| Database | PostgreSQL 15+ |
| ORM | Prisma (type-safe, good migration story) |
| Cache / timer state | Redis 7+ via `ioredis` |
| Auth | `jsonwebtoken` + `bcryptjs` |
| Validation | `zod` |
| Env config | `dotenv` |

---

## 3. Environment Variables

```
PORT=3000
DATABASE_URL=postgresql://user:pass@localhost:5432/lyaba
REDIS_URL=redis://localhost:6379
JWT_SECRET=<strong-random-secret>
JWT_EXPIRES_IN=7d
BASE_URL=http://localhost:3000/api   # for reference only
```

---

## 4. Database Schema

### 4.1 Entity-Relationship Summary

```
users ──< room_participants >── rooms
players ──< auction_players >── rooms
rooms ──< active_auctions (1:1 at any time)
active_auctions ──< bid_history
```

---

### 4.2 Table Definitions (PostgreSQL DDL)

```sql
-- ─── USERS ────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── ROOMS ────────────────────────────────────────────────────────────────────
CREATE TYPE room_status AS ENUM ('waiting', 'live', 'completed');

CREATE TABLE rooms (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             CHAR(6) NOT NULL UNIQUE,           -- e.g. "AX7K2P"
    created_by       UUID NOT NULL REFERENCES users(id),
    status           room_status NOT NULL DEFAULT 'waiting',
    timer_duration   INT NOT NULL DEFAULT 10,           -- seconds
    starting_budget  BIGINT NOT NULL DEFAULT 100000000, -- £100M in pence/minor unit
    min_increment    BIGINT NOT NULL DEFAULT 1000000,   -- £1M
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rooms_code ON rooms(code);
CREATE INDEX idx_rooms_status ON rooms(status);

-- ─── ROOM PARTICIPANTS ────────────────────────────────────────────────────────
-- One row per user per room. Tracks per-room budget.
CREATE TABLE room_participants (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id   UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id),
    budget    BIGINT NOT NULL,   -- starts at room.starting_budget, deducted on wins
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, user_id)
);

CREATE INDEX idx_rp_room ON room_participants(room_id);
CREATE INDEX idx_rp_user ON room_participants(user_id);

-- ─── PLAYERS ──────────────────────────────────────────────────────────────────
-- Global player pool (seeded once). NOT room-specific.
CREATE TYPE player_position AS ENUM ('GK', 'DEF', 'MID', 'FWD');

CREATE TABLE players (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      TEXT NOT NULL,
    position  player_position NOT NULL,
    team      TEXT NOT NULL,
    rating    INT NOT NULL CHECK (rating BETWEEN 1 AND 99),
    photo_url TEXT
);

-- ─── AUCTION PLAYERS ──────────────────────────────────────────────────────────
-- Per-room copy of the player pool. Tracks who owns each player in a room.
CREATE TYPE auction_player_status AS ENUM ('available', 'active', 'sold', 'unsold');

CREATE TABLE auction_players (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    player_id   UUID NOT NULL REFERENCES players(id),
    status      auction_player_status NOT NULL DEFAULT 'available',
    owned_by    UUID REFERENCES users(id),      -- set when sold
    sold_amount BIGINT,                          -- winning bid amount
    UNIQUE (room_id, player_id)
);

CREATE INDEX idx_ap_room ON auction_players(room_id);
CREATE INDEX idx_ap_room_status ON auction_players(room_id, status);

-- ─── ACTIVE AUCTIONS ──────────────────────────────────────────────────────────
-- At most ONE active auction per room at any time.
CREATE TYPE auction_status AS ENUM ('active', 'sold');

CREATE TABLE active_auctions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID NOT NULL UNIQUE REFERENCES rooms(id) ON DELETE CASCADE,
    player_id           UUID NOT NULL REFERENCES players(id),
    nominated_by        UUID NOT NULL REFERENCES users(id),
    current_bid         BIGINT NOT NULL,
    current_bidder      UUID REFERENCES users(id),      -- NULL until first bid
    current_bidder_name TEXT,
    timer_ends_at       BIGINT NOT NULL,                -- Unix epoch ms (server time)
    status              auction_status NOT NULL DEFAULT 'active',
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMPTZ
);

-- ─── BID HISTORY ──────────────────────────────────────────────────────────────
CREATE TABLE bid_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id      UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    auction_id   UUID NOT NULL REFERENCES active_auctions(id),
    user_id      UUID NOT NULL REFERENCES users(id),
    display_name TEXT NOT NULL,   -- denormalized for fast history reads
    amount       BIGINT NOT NULL,
    placed_at    BIGINT NOT NULL  -- Unix epoch ms
);

CREATE INDEX idx_bh_auction ON bid_history(auction_id);
CREATE INDEX idx_bh_room ON bid_history(room_id, placed_at DESC);
```

---

### 4.3 Prisma Schema (equivalent)

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model User {
  id           String   @id @default(uuid())
  email        String   @unique
  passwordHash String   @map("password_hash")
  displayName  String   @map("display_name")
  createdAt    DateTime @default(now()) @map("created_at")

  rooms            RoomParticipant[]
  nominations      ActiveAuction[]   @relation("NominatedBy")
  bids             BidHistory[]
  wonPlayers       AuctionPlayer[]
  currentBidding   ActiveAuction[]   @relation("CurrentBidder")
  createdRooms     Room[]

  @@map("users")
}

model Room {
  id              String     @id @default(uuid())
  code            String     @unique @db.Char(6)
  createdBy       String     @map("created_by")
  creator         User       @relation(fields: [createdBy], references: [id])
  status          RoomStatus @default(waiting)
  timerDuration   Int        @default(10) @map("timer_duration")
  startingBudget  BigInt     @default(100000000) @map("starting_budget")
  minIncrement    BigInt     @default(1000000) @map("min_increment")
  createdAt       DateTime   @default(now()) @map("created_at")

  participants    RoomParticipant[]
  auctionPlayers  AuctionPlayer[]
  activeAuction   ActiveAuction?
  bidHistory      BidHistory[]

  @@map("rooms")
}

enum RoomStatus {
  waiting
  live
  completed
}

model RoomParticipant {
  id       String   @id @default(uuid())
  roomId   String   @map("room_id")
  userId   String   @map("user_id")
  budget   BigInt
  joinedAt DateTime @default(now()) @map("joined_at")

  room Room @relation(fields: [roomId], references: [id], onDelete: Cascade)
  user User @relation(fields: [userId], references: [id])

  @@unique([roomId, userId])
  @@map("room_participants")
}

model Player {
  id       String         @id @default(uuid())
  name     String
  position PlayerPosition
  team     String
  rating   Int
  photoUrl String?        @map("photo_url")

  auctionSlots    AuctionPlayer[]
  activeAuctions  ActiveAuction[]

  @@map("players")
}

enum PlayerPosition {
  GK
  DEF
  MID
  FWD
}

model AuctionPlayer {
  id          String              @id @default(uuid())
  roomId      String              @map("room_id")
  playerId    String              @map("player_id")
  status      AuctionPlayerStatus @default(available)
  ownedBy     String?             @map("owned_by")
  soldAmount  BigInt?             @map("sold_amount")

  room   Room    @relation(fields: [roomId], references: [id], onDelete: Cascade)
  player Player  @relation(fields: [playerId], references: [id])
  owner  User?   @relation(fields: [ownedBy], references: [id])

  @@unique([roomId, playerId])
  @@map("auction_players")
}

enum AuctionPlayerStatus {
  available
  active
  sold
  unsold
}

model ActiveAuction {
  id                String        @id @default(uuid())
  roomId            String        @unique @map("room_id")
  playerId          String        @map("player_id")
  nominatedBy       String        @map("nominated_by")
  currentBid        BigInt        @map("current_bid")
  currentBidder     String?       @map("current_bidder")
  currentBidderName String?       @map("current_bidder_name")
  timerEndsAt       BigInt        @map("timer_ends_at")
  status            AuctionStatus @default(active)
  startedAt         DateTime      @default(now()) @map("started_at")
  endedAt           DateTime?     @map("ended_at")

  room      Room       @relation(fields: [roomId], references: [id], onDelete: Cascade)
  player    Player     @relation(fields: [playerId], references: [id])
  nominator User       @relation("NominatedBy", fields: [nominatedBy], references: [id])
  bidder    User?      @relation("CurrentBidder", fields: [currentBidder], references: [id])
  bids      BidHistory[]

  @@map("active_auctions")
}

enum AuctionStatus {
  active
  sold
}

model BidHistory {
  id          String @id @default(uuid())
  roomId      String @map("room_id")
  auctionId   String @map("auction_id")
  userId      String @map("user_id")
  displayName String @map("display_name")
  amount      BigInt
  placedAt    BigInt @map("placed_at")

  room    Room          @relation(fields: [roomId], references: [id], onDelete: Cascade)
  auction ActiveAuction @relation(fields: [auctionId], references: [id])
  user    User          @relation(fields: [userId], references: [id])

  @@map("bid_history")
}
```

---

## 5. REST API

Base path: `/api`  
All protected routes require `Authorization: Bearer <JWT>`.

---

### 5.1 Auth

#### `POST /api/auth/register`

Request:
```json
{ "email": "user@example.com", "password": "secret123", "displayName": "Mukhesh" }
```

Response `200`:
```json
{
  "token": "<JWT>",
  "user": { "id": "uuid", "displayName": "Mukhesh", "budget": 0, "roomId": null }
}
```

Errors: `400` email already exists, `422` validation failure.

Implementation notes:
- Hash password with `bcrypt` (rounds = 12)
- Sign JWT: `{ sub: user.id, displayName }`, expiry from `JWT_EXPIRES_IN`
- `budget` in `User` response is `0` outside a room context

---

#### `POST /api/auth/login`

Request:
```json
{ "email": "user@example.com", "password": "secret123" }
```

Response `200`: same shape as register.

Errors: `401` invalid credentials.

---

#### `GET /api/auth/me`

Auth: required.

Response `200`:
```json
{ "id": "uuid", "displayName": "Mukhesh", "budget": 0, "roomId": null }
```

`budget` and `roomId` — return values from the user's most recently joined active room (if any), else `0` and `null`.

---

### 5.2 Rooms

#### `POST /api/rooms`

Auth: required.

Request:
```json
{ "startingBudget": 100000000, "minIncrement": 1000000, "timerDuration": 10 }
```

Response `201`:
```json
{
  "id": "uuid",
  "code": "AX7K2P",
  "createdBy": "userId",
  "status": "waiting",
  "timerDuration": 10,
  "startingBudget": 100000000,
  "minIncrement": 1000000
}
```

Implementation notes:
- Generate a unique 6-char alphanumeric code (uppercase). Retry on collision.
- Auto-add creator to `room_participants` with `budget = startingBudget`.
- Copy the full player pool into `auction_players` for this room (status = `available`).

---

#### `POST /api/rooms/:code/join`

Auth: required. Path param is the **room code** (not id).

Response `200`: Room object (same shape as above).

Errors: `404` code not found, `400` room not in `waiting` status, `409` already joined.

Implementation:
- Look up room by `code`
- Insert into `room_participants` with `budget = room.startingBudget`
- Return room object

---

#### `GET /api/rooms/:id`

Auth: required.

Response `200`: Room object.

---

#### `POST /api/rooms/:id/start`

Auth: required. Only callable by room creator.

Response `200`: `{ "ok": true }`

Errors: `403` not creator, `400` room not in `waiting` status, `400` fewer than 2 participants.

Implementation:
- Set `rooms.status = 'live'`
- Broadcast `nomination_open` via WebSocket to all connected users in the room

---

#### `GET /api/rooms/:id/results`

Auth: required.

Response `200`:
```json
[
  {
    "userId": "uuid",
    "displayName": "Mukhesh",
    "budget": 45000000,
    "players": [
      {
        "id": "uuid",
        "name": "Salah",
        "position": "FWD",
        "team": "Liverpool",
        "rating": 89,
        "photoUrl": "https://...",
        "status": "sold",
        "ownedBy": "userId"
      }
    ]
  }
]
```

Sorted by `budget DESC` (most remaining budget first, as the client also sorts this way).

---

### 5.3 Players

#### `GET /api/players`

Auth: required.

Response `200`: Array of Player objects.

```json
[
  {
    "id": "uuid",
    "name": "Salah",
    "position": "FWD",
    "team": "Liverpool",
    "rating": 89,
    "photoUrl": "https://...",
    "status": "available",
    "ownedBy": null
  }
]
```

Note: `status` and `ownedBy` must be room-scoped. Client calls this from MySquadScreen which is always within a room context, so either:
- Accept `?roomId=` query param, or
- Derive the user's current room from JWT context

Recommendation: `GET /api/players?roomId=<id>` — return `auction_players` joined with `players` for that room.

---

#### `GET /api/players/:id`

Auth: required.

Response `200`: Single player object (same shape, global — not room-scoped).

---

### 5.4 Time Sync

#### `GET /api/time`

No auth required.

Response `200`:
```json
{ "serverTime": 1715692800000 }
```

`serverTime` = `Date.now()` (Unix ms). Client uses this once on WS connect to calculate clock offset.

---

## 6. WebSocket Protocol

### 6.1 Connection

```
ws://<host>:<port>?token=<JWT>&roomId=<roomId>
```

On connect, server must:
1. Validate JWT — close with code `4001` if invalid.
2. Verify user is a participant in `roomId` — close with code `4003` if not.
3. Register the socket in an in-memory map: `roomSockets: Map<roomId, Set<WebSocket>>`.
4. Emit `room_joined` to the connecting client only.

### 6.2 Message Envelope

All messages (both directions):
```json
{ "type": "string", "data": { ... } }
```

`data` is omitted or `null` for events with no payload (e.g. `nomination_open`).

---

### 6.3 Client → Server Messages

#### `bid`
```json
{ "type": "bid", "data": { "amount": 5000000 } }
```
`amount` = absolute bid amount (not increment).

#### `nominate`
```json
{ "type": "nominate", "data": { "playerId": "uuid", "baseBid": 1000000 } }
```

---

### 6.4 Server → Client Messages

#### `room_joined` (to connecting client only)
```json
{
  "type": "room_joined",
  "data": {
    "users": [
      { "id": "uuid", "displayName": "Mukhesh", "budget": 100000000, "roomId": "uuid" }
    ],
    "currentState": null
  }
}
```
`currentState` is `null` if no active auction, otherwise an `ActiveAuction` object (see below).

#### `auction_started` (broadcast to room)
```json
{
  "type": "auction_started",
  "data": {
    "playerId": "uuid",
    "player": { "id": "uuid", "name": "Salah", "position": "FWD", "team": "Liverpool", "rating": 89, "photoUrl": "...", "status": "available", "ownedBy": null },
    "nominatedBy": "userId",
    "currentBid": 1000000,
    "currentBidder": "",
    "currentBidderName": "",
    "timerEndsAt": 1715692810000,
    "status": "active"
  }
}
```

#### `new_bid` (broadcast to room)
```json
{
  "type": "new_bid",
  "data": {
    "userId": "uuid",
    "displayName": "Mukhesh",
    "amount": 5000000,
    "timerEndsAt": 1715692815000
  }
}
```

#### `bid_rejected` (to bidder only)
```json
{
  "type": "bid_rejected",
  "data": { "reason": "Insufficient budget" }
}
```

Possible reasons: `"Insufficient budget"`, `"You are already the highest bidder"`, `"Bid must be at least £Xm more than current bid"`, `"No active auction"`.

#### `player_sold` (broadcast to room)
```json
{
  "type": "player_sold",
  "data": {
    "playerId": "uuid",
    "winner": "userId",
    "winnerName": "Mukhesh",
    "amount": 8000000
  }
}
```

#### `budget_updated` (broadcast to room — all clients update their display)
```json
{
  "type": "budget_updated",
  "data": { "userId": "uuid", "newBudget": 92000000 }
}
```

Sent immediately after `player_sold`. All clients receive it so they can display each other's remaining budgets.

#### `nomination_open` (broadcast to room)
```json
{ "type": "nomination_open" }
```

Sent when: auction starts (after `POST /rooms/:id/start`) and after each player is sold/unsold, if more players remain.

#### `auction_complete` (broadcast to room)
```json
{
  "type": "auction_complete",
  "data": {
    "results": [
      {
        "userId": "uuid",
        "displayName": "Mukhesh",
        "budget": 92000000,
        "players": [ { ...Player } ]
      }
    ]
  }
}
```

Sent when all `auction_players` for the room are `sold` or `unsold`.

#### `error` (to relevant client)
```json
{ "type": "error", "data": { "message": "Something went wrong" } }
```

---

## 7. Core Business Logic

### 7.1 Auction State Machine

```
Room:  waiting → live → completed

Per player:  available → active → sold
                                → unsold (if no bids when timer expires)
```

### 7.2 Starting the Auction

```
POST /api/rooms/:id/start
  → rooms.status = 'live'
  → broadcast nomination_open to room
```

### 7.3 Nomination Flow

```
Client sends: { type: "nominate", data: { playerId, baseBid } }

Server:
  1. Validate room is 'live'
  2. Validate no active auction exists for this room
  3. Validate player is 'available' in this room's auction_players
  4. Validate baseBid >= room.minIncrement
  5. Create active_auctions row:
       - currentBid = baseBid
       - currentBidder = null
       - timerEndsAt = Date.now() + (room.timerDuration * 1000)
  6. Set auction_players.status = 'active' for this player
  7. Schedule timer: setTimeout(onTimerExpired, room.timerDuration * 1000)
  8. Broadcast auction_started to room
```

### 7.4 Bid Validation

```
Client sends: { type: "bid", data: { amount } }

Server validates:
  1. Active auction exists for room
  2. auction.status == 'active'
  3. amount >= auction.currentBid + room.minIncrement
  4. bidder != auction.currentBidder  (can't outbid yourself)
  5. participant.budget >= amount

On failure → send bid_rejected to sender only.

On success:
  1. Cancel current timer (clearTimeout)
  2. Update active_auctions:
       currentBid = amount
       currentBidder = userId
       currentBidderName = displayName
       timerEndsAt = Date.now() + (room.timerDuration * 1000)
  3. Insert bid_history row
  4. Broadcast new_bid to room
  5. Schedule new timer
```

### 7.5 Timer Expiry

```
onTimerExpired(roomId):
  1. Fetch active_auction for room
  2. If currentBidder is null:
       → Player unsold
       → auction_players.status = 'unsold'
  3. Else:
       → Player sold
       → auction_players.status = 'sold'
       → auction_players.owned_by = currentBidder
       → auction_players.sold_amount = currentBid
       → room_participants.budget -= currentBid  (for winner)
       → Broadcast player_sold
       → Broadcast budget_updated (winner's new budget)

  4. active_auctions.status = 'sold', ended_at = NOW()
  5. Delete active_auctions row (or mark ended)

  6. Check if any auction_players for room still have status 'available':
       YES → Broadcast nomination_open
       NO  → rooms.status = 'completed'
             → Broadcast auction_complete with results
```

### 7.6 Timer Storage

Store each room's active timer handle in a server-side Map:

```typescript
const timers = new Map<string, NodeJS.Timeout>(); // roomId → timer handle

function setRoomTimer(roomId: string, ms: number, callback: () => void) {
  clearRoomTimer(roomId);
  const handle = setTimeout(callback, ms);
  timers.set(roomId, handle);
}

function clearRoomTimer(roomId: string) {
  const handle = timers.get(roomId);
  if (handle) clearTimeout(handle);
  timers.delete(roomId);
}
```

Also store `timerEndsAt` in Redis (`auction:timer:{roomId}`) so reconnecting clients get the correct value from `currentState` in `room_joined`.

### 7.7 Reconnect / Late Join

When a client reconnects (sends WS connection with a room that has an active auction):
- `room_joined.currentState` contains the full `ActiveAuction` snapshot including `timerEndsAt`
- Client derives remaining time from `timerEndsAt - (serverTime + offset)` — no re-sync needed

---

## 8. WebSocket Connection Management

```typescript
// In-memory socket registry
const roomSockets = new Map<string, Map<string, WebSocket>>();
// roomId → Map<userId, WebSocket>

function broadcast(roomId: string, message: object, excludeUserId?: string) {
  const room = roomSockets.get(roomId);
  if (!room) return;
  const text = JSON.stringify(message);
  for (const [userId, ws] of room) {
    if (userId !== excludeUserId && ws.readyState === WebSocket.OPEN) {
      ws.send(text);
    }
  }
}

function sendToUser(roomId: string, userId: string, message: object) {
  const ws = roomSockets.get(roomId)?.get(userId);
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}
```

On disconnect: remove from `roomSockets`. Do NOT clear game state — player may reconnect.

---

## 9. API Error Response Format

All errors return:
```json
{ "error": "Human-readable message" }
```

| Scenario | HTTP Status |
|---|---|
| Validation failure | 422 |
| Unauthenticated | 401 |
| Forbidden (not owner) | 403 |
| Not found | 404 |
| Business rule violation | 400 |
| Conflict (already exists) | 409 |
| Internal server error | 500 |

---

## 10. Authentication Details

### JWT Payload
```json
{ "sub": "userId", "displayName": "Mukhesh", "iat": 1715692800, "exp": 1716297600 }
```

### REST — Auth Middleware
```
Authorization: Bearer <token>
```
Attach decoded user to `req.user`.

### WebSocket — Auth
Token passed as query param: `?token=<JWT>&roomId=<roomId>`  
Validate on upgrade before accepting connection. Close with code `4001` on invalid token.

---

## 11. Seed Data — Players

Seed at least 30 players across EPL/LaLiga. Minimum fields:

```json
[
  { "name": "Mohamed Salah",    "position": "FWD", "team": "Liverpool",  "rating": 89 },
  { "name": "Erling Haaland",   "position": "FWD", "team": "Man City",   "rating": 91 },
  { "name": "Kevin De Bruyne",  "position": "MID", "team": "Man City",   "rating": 91 },
  { "name": "Alisson",          "position": "GK",  "team": "Liverpool",  "rating": 88 },
  { "name": "Virgil van Dijk",  "position": "DEF", "team": "Liverpool",  "rating": 88 },
  { "name": "Vinicius Jr",      "position": "FWD", "team": "Real Madrid","rating": 91 },
  { "name": "Jude Bellingham",  "position": "MID", "team": "Real Madrid","rating": 89 },
  ...
]
```

Use a Prisma seed script (`prisma/seed.ts`).

---

## 12. Sequence Diagram — Full Auction Round

```
Client A (admin)    Client B           Server
     |                  |                 |
     |──POST /rooms/start──────────────>  |
     |                  |   nomination_open (broadcast)
     |<──────────────────────────────────|
     |                  |<──────────────|
     |                  |                 |
     |──WS nominate ──────────────────>   |  (creates active_auction, sets timer)
     |                  |  auction_started (broadcast)
     |<──────────────────────────────────|
     |                  |<──────────────|
     |                  |                 |
     |                  |──WS bid ──────>  |  (validates, updates, resets timer)
     |                  |  new_bid (broadcast)
     |<──────────────────────────────────|
     |                  |<──────────────|
     |                  |                 |
     |                  |   [timer fires after 10s of silence]
     |                  |  player_sold (broadcast)
     |<──────────────────────────────────|
     |                  |<──────────────|
     |                  |  budget_updated (broadcast)
     |<──────────────────────────────────|
     |                  |<──────────────|
     |                  |  nomination_open (broadcast)  ← next round
     |<──────────────────────────────────|
     |                  |<──────────────|
```

---

## 13. Edge Cases to Handle

| Case | Handling |
|---|---|
| User bids exactly at timer expiry | Mutex/lock on `onTimerExpired` — if auction already closed, reject bid |
| Admin disconnects before starting | No impact — REST call is atomic, WS not needed to start |
| Bidder disconnects mid-auction | Their bid stays valid; they can reconnect and it resumes |
| All players go unsold | `auction_complete` fires normally |
| Duplicate room code on creation | Retry with new code (max 5 attempts, then 500) |
| User joins room already `live` | `409` or allow late join (join as spectator with full budget, cannot bid on in-progress auction) — choose one policy |
| Nomination of a sold/active player | Return WS `error` event |
| minIncrement = 0 | Validate min 1 in request body |

---

## 14. Project Structure (Node.js)

```
src/
├── index.ts                    → Express + WS server bootstrap
├── config.ts                   → Env vars, validated with zod
├── db/
│   ├── prisma.ts               → PrismaClient singleton
│   └── seed.ts                 → Player seed data
├── middleware/
│   ├── auth.ts                 → JWT middleware (REST)
│   └── errorHandler.ts         → Global error handler
├── routes/
│   ├── auth.ts                 → /api/auth/*
│   ├── rooms.ts                → /api/rooms/*
│   ├── players.ts              → /api/players/*
│   └── time.ts                 → /api/time
├── ws/
│   ├── wsServer.ts             → WebSocket upgrade handler, auth, registry
│   ├── wsHandlers.ts           → bid, nominate message handlers
│   └── broadcaster.ts          → broadcast(), sendToUser()
├── services/
│   ├── auctionService.ts       → Core auction logic (nominate, bid, timer)
│   ├── roomService.ts          → Create/join/start room
│   └── authService.ts          → register, login, getMe
└── types/
    └── index.ts                → Shared TypeScript types
```

---

## 15. Quick-Start Checklist

- [ ] Set up PostgreSQL + run migrations (`prisma migrate dev`)
- [ ] Seed players (`npx ts-node prisma/seed.ts`)
- [ ] Set up Redis
- [ ] Implement REST routes in order: auth → rooms → players → time
- [ ] Implement WebSocket server with auth on upgrade
- [ ] Implement `room_joined` broadcast on connect
- [ ] Implement `nominate` handler
- [ ] Implement `bid` handler with validation
- [ ] Implement `onTimerExpired` with `player_sold` + `budget_updated` + `nomination_open` / `auction_complete`
- [ ] Test with 2 emulators (or emulator + physical device)
- [ ] Update `BASE_URL` in Android `BuildConfig` to point at your machine's LAN IP
