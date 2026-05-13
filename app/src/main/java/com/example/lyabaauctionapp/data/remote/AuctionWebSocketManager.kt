package com.example.lyabaauctionapp.data.remote

import com.example.lyabaauctionapp.BuildConfig
import com.example.lyabaauctionapp.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuctionWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<AuctionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AuctionEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var currentRoomId: String = ""
    private var currentToken: String = ""
    private var reconnectAttempts = 0

    fun connect(roomId: String, token: String) {
        currentRoomId = roomId
        currentToken = token
        reconnectAttempts = 0
        openWebSocket()
    }

    fun disconnect() {
        currentRoomId = ""
        currentToken = ""
        webSocket?.close(1000, "User left")
        webSocket = null
    }

    private fun openWebSocket() {
        val url = "${BuildConfig.WS_URL}?token=$currentToken&roomId=$currentRoomId"
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val msg = gson.fromJson(text, WsMessage::class.java)
                val event = parseEvent(msg) ?: return
                scope.launch { _events.emit(event) }
            } catch (_: Exception) {}
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code != 1000) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (currentRoomId.isEmpty()) return
        scope.launch {
            val delayMs = minOf(1000L shl reconnectAttempts, 30_000L)
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(5)
            delay(delayMs)
            openWebSocket()
        }
    }

    private fun parseEvent(msg: WsMessage): AuctionEvent? {
        val d = msg.data
        return try {
            when (msg.type) {
                "room_joined" -> {
                    val usersArr = d?.getAsJsonArray("users") ?: return null
                    val users = usersArr.map { gson.fromJson(it, User::class.java) }
                    val stateEl = d.get("currentState")
                    val state = if (stateEl != null && !stateEl.isJsonNull)
                        gson.fromJson(stateEl, ActiveAuction::class.java) else null
                    AuctionEvent.RoomJoined(users, state)
                }
                "auction_started" -> AuctionEvent.AuctionStarted(gson.fromJson(d, ActiveAuction::class.java))
                "new_bid" -> AuctionEvent.NewBid(
                    userId = d?.get("userId")?.asString ?: "",
                    displayName = d?.get("displayName")?.asString ?: "",
                    amount = d?.get("amount")?.asLong ?: 0L,
                    timerEndsAt = d?.get("timerEndsAt")?.asLong ?: 0L
                )
                "bid_rejected" -> AuctionEvent.BidRejected(d?.get("reason")?.asString ?: "Bid rejected")
                "player_sold" -> AuctionEvent.PlayerSold(
                    playerId = d?.get("playerId")?.asString ?: "",
                    winner = d?.get("winner")?.asString ?: "",
                    winnerName = d?.get("winnerName")?.asString ?: "",
                    amount = d?.get("amount")?.asLong ?: 0L
                )
                "budget_updated" -> AuctionEvent.BudgetUpdated(
                    userId = d?.get("userId")?.asString ?: "",
                    newBudget = d?.get("newBudget")?.asLong ?: 0L
                )
                "nomination_open" -> AuctionEvent.NominationOpen
                "auction_complete" -> {
                    val arr = d?.getAsJsonArray("results") ?: return null
                    val results = arr.map { gson.fromJson(it, AuctionResult::class.java) }
                    AuctionEvent.AuctionComplete(results)
                }
                "error" -> AuctionEvent.Error(d?.get("message")?.asString ?: "Unknown error")
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
