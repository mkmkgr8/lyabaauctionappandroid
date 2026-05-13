package com.example.lyabaauctionapp.ui.auction.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CountdownTimer(seconds: Int, modifier: Modifier = Modifier) {
    val timerColor by animateColorAsState(
        targetValue = when {
            seconds <= 3 -> MaterialTheme.colorScheme.error
            seconds <= 5 -> Color(0xFFE65100) // orange
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "timer_color"
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .background(timerColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = seconds.toString(),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = timerColor
            )
            Text(
                text = "sec",
                fontSize = 12.sp,
                color = timerColor.copy(alpha = 0.7f)
            )
        }
    }
}
