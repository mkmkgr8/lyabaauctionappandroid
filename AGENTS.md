# AGENTS.md — Auction App (Android)

## Project Overview
Android client for a real-time soccer player auction app. 20+ friends join a room, bid on soccer players in real-time, with a 10-second countdown timer. Highest bidder wins, budget auto-deducted. Think IPL auction but for EPL/LaLiga players.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (NO XML layouts)
- **Architecture:** MVVM + StateFlow
- **Networking (REST):** Retrofit2 + OkHttp
- **Networking (WebSocket):** OkHttp WebSocket (built into OkHttp, no extra dependency)
- **DI:** Hilt (Dagger)
- **JSON:** Gson or Moshi
- **Navigation:** Jetpack Navigation Compose
- **Image Loading:** Coil (Compose-native)
- **Min SDK:** 26 (Android 8.0)

## Architecture Pattern

```
UI Layer (Compose)
    ↓ observes StateFlow
ViewModel
    ↓ calls
Repository
    ↓ uses
DataSource (REST via Retrofit + WebSocket via OkHttp)
```

### State Management
- Each screen has its own ViewModel
- UI state is a single sealed class/data class exposed as StateFlow
- WebSocket events flow through a shared AuctionWebSocketManager → emits to a SharedFlow → ViewModels collect

### WebSocket Manager (Singleton)
```
AuctionWebSocketManager
  - connect(roomId, token)
  - disconnect()
  - sendBid(amount)
  - sendNomination(playerId, baseBid)
  - events: SharedFlow<AuctionEvent>  ← all incoming events flow here
```

ViewModels collect from `events` and update their own UI state.

## Backend Connection
- **REST Base URL:** configurable in BuildConfig (default: `http://10.0.2.2:3000/api` for emulator, `http://<local-ip>:3000/api` for physical device)
- **WebSocket URL:** `ws://10.0.2.2:3000` for emulator
- Auth: JWT token stored in DataStore, sent in `Authorization: Bearer <token>` header for REST and as query param for WebSocket

## Screens & Navigation

```
SplashScreen
    ↓
AuthScreen (Login / Register)
    ↓
HomeScreen (Create Room / Join Room)
    ↓
LobbyScreen (see participants, wait for admin to start)
    ↓
AuctionScreen (THE MAIN SCREEN — live bidding)
    ↓
ResultsScreen (final squads, leaderboard)

Side screens (accessible from nav):
  MySquadScreen (players you've won + remaining budget)
  PlayerListScreen (browse all available players)
```

## Screen Details

### AuctionScreen (Most Complex — Design Carefully)
This is the core screen. Must show:
- **Player Card:** photo, name, position, team, rating of player being auctioned
- **Current Bid:** large, prominent display of current highest bid amount
- **Current Bidder:** who holds the highest bid (highlight if it's you)
- **Countdown Timer:** 10-second timer, resets on each new bid. Use circular or linear progress indicator. Visual urgency when < 3 seconds (color change / animation)
- **Bid Controls:** 
  - Quick bid buttons: +1M, +2M, +5M, +10M (adds to current bid)
  - Custom bid input field
  - BID button (disabled if insufficient budget or if you're already highest bidder)
- **Your Budget:** always visible, updates in real-time when you win
- **Bid History:** scrollable list showing who bid what (most recent on top)
- **Status Banner:** "SOLD to [name] for [amount]!" when timer expires

### LobbyScreen
- Room code displayed prominently (for sharing)
- List of joined participants
- Start Auction button (visible only to room creator)
- Player pool info (how many players available)

### HomeScreen
- Create Room button → navigates to room settings (timer duration, starting budget, min increment)
- Join Room → text field for room code
- Recent rooms list (stored locally)

### MySquadScreen
- Grid/list of won players with position badges
- Total spent vs remaining budget
- Squad composition (how many GK/DEF/MID/FWD)

### ResultsScreen
- All users ranked by squad value or remaining budget
- Expandable cards showing each user's squad
- Share results button

## Data Models

```kotlin
data class User(
    val id: String,
    val displayName: String,
    val budget: Long,
    val roomId: String?
)

data class Player(
    val id: String,
    val name: String,
    val position: String,        // GK, DEF, MID, FWD
    val team: String,
    val rating: Int,
    val photoUrl: String?,
    val status: String,          // available, sold, unsold
    val ownedBy: String?
)

data class ActiveAuction(
    val playerId: String,
    val player: Player,
    val nominatedBy: String,
    val currentBid: Long,
    val currentBidder: String,
    val currentBidderName: String,
    val timerEndsAt: Long,       // server timestamp
    val status: String           // active, sold
)

data class BidHistoryItem(
    val userId: String,
    val displayName: String,
    val amount: Long,
    val timestamp: Long
)

data class Room(
    val id: String,
    val code: String,
    val createdBy: String,
    val status: String,          // waiting, live, completed
    val timerDuration: Int,      // seconds (default 10)
    val startingBudget: Long,    // default 100_000_000
    val minIncrement: Long       // default 1_000_000
)

// WebSocket events (sealed class)
sealed class AuctionEvent {
    data class RoomJoined(val users: List<User>, val currentState: ActiveAuction?) : AuctionEvent()
    data class AuctionStarted(val auction: ActiveAuction) : AuctionEvent()
    data class NewBid(val userId: String, val displayName: String, val amount: Long, val timerEndsAt: Long) : AuctionEvent()
    data class BidRejected(val reason: String) : AuctionEvent()
    data class PlayerSold(val playerId: String, val winner: String, val winnerName: String, val amount: Long) : AuctionEvent()
    data class BudgetUpdated(val userId: String, val newBudget: Long) : AuctionEvent()
    data object NominationOpen : AuctionEvent()
    data class AuctionComplete(val results: List<AuctionResult>) : AuctionEvent()
    data class Error(val message: String) : AuctionEvent()
}
```

## Timer Display Logic
- Server sends `timerEndsAt` (absolute server timestamp)
- Client calculates: `remainingSeconds = (timerEndsAt - serverTime) / 1000`
- Sync server time once on connect: `offset = serverTime - System.currentTimeMillis()`
- Use `LaunchedEffect` + `delay(100)` loop to update countdown every 100ms
- NEVER run the timer independently on the client — always derive from server timestamp
- Color changes: green (>5s) → yellow (3-5s) → red (<3s)

## Project Structure

```
app/src/main/java/com/yourpackage/auction/
├── AuctionApp.kt                          → Hilt Application class
├── MainActivity.kt                        → Single activity, hosts NavHost
├── navigation/
│   └── NavGraph.kt                        → All routes and navigation
├── data/
│   ├── remote/
│   │   ├── ApiService.kt                  → Retrofit interface (REST endpoints)
│   │   ├── AuctionWebSocketManager.kt     → OkHttp WebSocket singleton
│   │   └── AuthInterceptor.kt            → Adds JWT to requests
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── RoomRepository.kt
│   │   ├── PlayerRepository.kt
│   │   └── AuctionRepository.kt
│   ├── model/                             → Data classes (see above)
│   └── local/
│       └── TokenManager.kt               → DataStore for JWT token
├── di/
│   └── AppModule.kt                      → Hilt modules (Retrofit, OkHttp, Redis client providers)
├── ui/
│   ├── auth/
│   │   ├── AuthScreen.kt
│   │   └── AuthViewModel.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── lobby/
│   │   ├── LobbyScreen.kt
│   │   └── LobbyViewModel.kt
│   ├── auction/
│   │   ├── AuctionScreen.kt              → THE MAIN SCREEN
│   │   ├── AuctionViewModel.kt
│   │   └── components/
│   │       ├── PlayerCard.kt
│   │       ├── BidControls.kt
│   │       ├── CountdownTimer.kt
│   │       ├── BidHistory.kt
│   │       └── SoldBanner.kt
│   ├── squad/
│   │   ├── MySquadScreen.kt
│   │   └── MySquadViewModel.kt
│   ├── results/
│   │   ├── ResultsScreen.kt
│   │   └── ResultsViewModel.kt
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
└── util/
    ├── TimeSync.kt                       → Server time offset calculation
    └── Extensions.kt                     → Format currency, etc.
```

## Commands
- Build: `./gradlew assembleDebug`
- Lint: `./gradlew lint`
- Unit tests: `./gradlew testDebugUnitTest`
- Install on device: `./gradlew installDebug`
- Clean: `./gradlew clean`

## Rules
- After every code change, run `./gradlew assembleDebug`
- If build fails, read error output and fix before moving on
- ALL UI must use Jetpack Compose — no XML layouts, no fragments
- Follow MVVM strictly — no business logic in Composables
- Use StateFlow for all UI state — no LiveData
- Timer is ALWAYS derived from server timestamp — never run independent client timer
- WebSocket reconnection: auto-reconnect with exponential backoff on disconnect
- Handle configuration changes (rotation) — ViewModel survives, WebSocket stays connected
- Budget display must update in real-time across all screens
- Never trust client-side validation alone — server is authoritative for bids
- Use Material 3 theming
- Show loading states and error states for all network operations
- Show Snackbar for bid rejections with the reason
