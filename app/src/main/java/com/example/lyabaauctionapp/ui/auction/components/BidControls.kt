package com.example.lyabaauctionapp.ui.auction.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyabaauctionapp.util.formatBudget

@Composable
fun BidControls(
    currentBid: Long,
    minIncrement: Long,
    myBudget: Long,
    iAmHighestBidder: Boolean,
    isBidding: Boolean,
    onBid: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var customBid by remember(currentBid) { mutableStateOf("") }
    val quickAmounts = listOf(minIncrement, minIncrement * 2, minIncrement * 5, minIncrement * 10)
    val minBid = currentBid + minIncrement
    val canBid = !iAmHighestBidder && !isBidding

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickAmounts.forEach { increment ->
                val bidAmount = currentBid + increment
                OutlinedButton(
                    onClick = { onBid(bidAmount) },
                    modifier = Modifier.weight(1f),
                    enabled = canBid && bidAmount <= myBudget,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text("+${increment.formatBudget()}", fontSize = 12.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customBid,
                onValueChange = { customBid = it.filter(Char::isDigit) },
                label = { Text("Custom bid") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("min ${minBid.formatBudget()}") }
            )
            val customAmount = customBid.toLongOrNull() ?: 0L
            Button(
                onClick = { onBid(customAmount); customBid = "" },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically).height(56.dp),
                enabled = canBid && customAmount >= minBid && customAmount <= myBudget
            ) {
                if (isBidding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("BID", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (iAmHighestBidder) {
            Text(
                "You're the highest bidder!",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
