package com.example.lyabaauctionapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lyabaauctionapp.data.local.dao.PlayerDao
import com.example.lyabaauctionapp.data.local.dao.RoomDao
import com.example.lyabaauctionapp.data.local.entity.PlayerEntity
import com.example.lyabaauctionapp.data.local.entity.RoomEntity

@Database(entities = [PlayerEntity::class, RoomEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun roomDao(): RoomDao
}
