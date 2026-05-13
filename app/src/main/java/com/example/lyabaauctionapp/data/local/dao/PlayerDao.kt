package com.example.lyabaauctionapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.lyabaauctionapp.data.local.entity.PlayerEntity

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players")
    suspend fun getAll(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: String): PlayerEntity?

    @Upsert
    suspend fun upsertAll(players: List<PlayerEntity>)

    @Upsert
    suspend fun upsert(player: PlayerEntity)
}
