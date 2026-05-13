package com.example.lyabaauctionapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.lyabaauctionapp.data.model.Player

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: String,
    val team: String,
    val rating: Int,
    val photoUrl: String?,
    val status: String,
    val ownedBy: String?
)

fun PlayerEntity.toPlayer() = Player(id, name, position, team, rating, photoUrl, status, ownedBy)
fun Player.toEntity() = PlayerEntity(id, name, position, team, rating, photoUrl, status, ownedBy)
