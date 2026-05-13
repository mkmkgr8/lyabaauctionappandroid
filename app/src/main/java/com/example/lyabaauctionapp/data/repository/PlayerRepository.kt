package com.example.lyabaauctionapp.data.repository

import com.example.lyabaauctionapp.data.local.AppDatabase
import com.example.lyabaauctionapp.data.local.entity.toEntity
import com.example.lyabaauctionapp.data.local.entity.toPlayer
import com.example.lyabaauctionapp.data.model.Player
import com.example.lyabaauctionapp.data.remote.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val api: ApiService,
    db: AppDatabase
) {
    private val dao = db.playerDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getPlayers(): List<Player> {
        val cached = dao.getAll()
        // Always kick off a background refresh so next call is fresh
        scope.launch { runCatching { dao.upsertAll(api.getPlayers().map { it.toEntity() }) } }
        // Return cache immediately if present; first call waits for network
        if (cached.isNotEmpty()) return cached.map { it.toPlayer() }
        return api.getPlayers().also { dao.upsertAll(it.map { p -> p.toEntity() }) }
    }

    suspend fun getPlayer(id: String): Player {
        val cached = dao.getById(id)
        scope.launch { runCatching { dao.upsert(api.getPlayer(id).toEntity()) } }
        return cached?.toPlayer() ?: api.getPlayer(id).also { dao.upsert(it.toEntity()) }
    }
}
