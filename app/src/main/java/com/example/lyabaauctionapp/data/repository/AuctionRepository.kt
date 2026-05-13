package com.example.lyabaauctionapp.data.repository

import com.example.lyabaauctionapp.data.model.AuctionResult
import com.example.lyabaauctionapp.data.model.AuctionEvent
import com.example.lyabaauctionapp.data.model.BidRequest
import com.example.lyabaauctionapp.data.model.NominateRequest
import com.example.lyabaauctionapp.data.local.TokenManager
import com.example.lyabaauctionapp.data.remote.ApiService
import com.example.lyabaauctionapp.data.remote.AuctionWebSocketManager
import com.example.lyabaauctionapp.util.TimeSync
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuctionRepository @Inject constructor(
    private val api: ApiService,
    private val wsManager: AuctionWebSocketManager,
    private val tokenManager: TokenManager,
    private val timeSync: TimeSync
) {
    val events: SharedFlow<AuctionEvent> = wsManager.events

    suspend fun connectToRoom(roomId: String) {
        val token = tokenManager.getToken() ?: return
        wsManager.connect(roomId, token)
        syncServerTime()
    }

    fun disconnectFromRoom() = wsManager.disconnect()

    // Throws on network failure or server rejection — caller must handle
    suspend fun placeBid(roomId: String, amount: Long) =
        api.placeBid(roomId, BidRequest(amount))

    // Throws on network failure or server rejection — caller must handle
    suspend fun nominatePlayer(roomId: String, playerId: String, baseBid: Long) =
        api.nominatePlayer(roomId, NominateRequest(playerId, baseBid))

    suspend fun getResults(roomId: String): List<AuctionResult> = api.getResults(roomId)

    fun serverNow(): Long = timeSync.now()

    fun remainingSeconds(timerEndsAt: Long): Int = timeSync.remainingSeconds(timerEndsAt)

    private suspend fun syncServerTime() {
        try {
            val response = api.getServerTime()
            timeSync.updateOffset(response.serverTime)
        } catch (_: Exception) {}
    }
}
