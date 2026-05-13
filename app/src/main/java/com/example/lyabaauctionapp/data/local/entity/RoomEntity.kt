package com.example.lyabaauctionapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.lyabaauctionapp.data.model.Room

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val code: String,
    val createdBy: String,
    val status: String,
    val timerDuration: Int,
    val startingBudget: Long,
    val minIncrement: Long,
    val lastAccessed: Long = System.currentTimeMillis()
)

fun RoomEntity.toRoom() = Room(id, code, createdBy, status, timerDuration, startingBudget, minIncrement)
fun Room.toEntity() = RoomEntity(id, code, createdBy, status, timerDuration, startingBudget, minIncrement)
