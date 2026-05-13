package com.example.lyabaauctionapp.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSync @Inject constructor() {
    private var offset = 0L

    fun updateOffset(serverTime: Long) {
        offset = serverTime - System.currentTimeMillis()
    }

    fun now(): Long = System.currentTimeMillis() + offset

    fun remainingSeconds(timerEndsAt: Long): Int =
        ((timerEndsAt - now() + 999L) / 1000L).toInt().coerceAtLeast(0)
}
