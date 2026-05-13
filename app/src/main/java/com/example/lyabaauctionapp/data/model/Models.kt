package com.example.lyabaauctionapp.data.model

import com.google.gson.annotations.SerializedName

// ─── Domain models ────────────────────────────────────────────────────────────

data class User(
    val id: String,
    val displayName: String,
    val budget: Long,
    val roomId: String?
)

data class Player(
    val id: String,
    val name: String,
    val position: String,       // GK, DEF, MID, FWD
    val team: String,
    val rating: Int,
    val photoUrl: String?,
    val status: String,         // available, sold, unsold
    val ownedBy: String?
)

data class ActiveAuction(
    val playerId: String,
    val player: Player,
    val nominatedBy: String,
    val currentBid: Long,
    val currentBidder: String,
    val currentBidderName: String,
    val timerEndsAt: Long,
    val status: String          // active, sold
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
    val status: String,         // waiting, live, completed
    val timerDuration: Int,
    val startingBudget: Long,
    val minIncrement: Long
)

data class AuctionResult(
    val userId: String,
    val displayName: String,
    val budget: Long,
    val players: List<Player>
)

// ─── Request models ───────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

data class CreateRoomRequest(
    val startingBudget: Long,
    val minIncrement: Long,
    val timerDuration: Int
)

data class NominateRequest(
    val playerId: String,
    val baseBid: Long
)

data class BidRequest(
    val amount: Long
)

// ─── Response models ──────────────────────────────────────────────────────────

data class AuthResponse(
    val token: String,
    val user: User
)

data class ServerTimeResponse(
    @SerializedName("serverTime") val serverTime: Long
)

// ─── WebSocket ────────────────────────────────────────────────────────────────

data class WsMessage(
    val type: String,
    val data: com.google.gson.JsonObject? = null
)

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