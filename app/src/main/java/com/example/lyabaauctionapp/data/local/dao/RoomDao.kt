package com.example.lyabaauctionapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.lyabaauctionapp.data.local.entity.RoomEntity

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: String): RoomEntity?

    @Query("SELECT * FROM rooms ORDER BY lastAccessed DESC LIMIT 5")
    suspend fun getRecent(): List<RoomEntity>

    @Upsert
    suspend fun upsert(room: RoomEntity)
}
