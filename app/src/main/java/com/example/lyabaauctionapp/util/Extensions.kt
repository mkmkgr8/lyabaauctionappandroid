package com.example.lyabaauctionapp.util

fun Long.formatBudget(): String = when {
    this >= 1_000_000L -> "£${this / 1_000_000}M"
    this >= 1_000L -> "£${this / 1_000}K"
    else -> "£$this"
}

fun Long.formatBid(): String = formatBudget()
