package com.example.lyabaauctionapp.data.repository

import com.example.lyabaauctionapp.data.local.AppDatabase
import com.example.lyabaauctionapp.data.local.entity.toEntity
import com.example.lyabaauctionapp.data.local.entity.toRoom
import com.example.lyabaauctionapp.data.model.CreateRoomRequest
import com.example.lyabaauctionapp.data.model.Room
import com.example.lyabaauctionapp.data.remote.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor(
    private val api: ApiService,
    db: AppDatabase
) {
    private val dao = db.roomDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun createRoom(startingBudget: Long, minIncrement: Long, timerDuration: Int): Room {
        val room = api.createRoom(CreateRoomRequest(startingBudget, minIncrement, timerDuration))
        dao.upsert(room.toEntity())
        return room
    }

    suspend fun joinRoom(code: String): Room {
        val room = api.joinRoom(code)
        dao.upsert(room.toEntity())
        return room
    }

    suspend fun getRoom(id: String): Room {
        val cached = dao.getById(id)
        // Refresh in background; lobby screen will re-load if needed
        scope.launch { runCatching { dao.upsert(api.getRoom(id).toEntity()) } }
        return cached?.toRoom() ?: api.getRoom(id).also { dao.upsert(it.toEntity()) }
    }

    suspend fun startAuction(roomId: String) = api.startAuction(roomId)

    suspend fun getRecentRooms(): List<Room> = dao.getRecent().map { it.toRoom() }
}
