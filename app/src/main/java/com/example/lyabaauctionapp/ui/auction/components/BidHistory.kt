package com.example.lyabaauctionapp.ui.auction.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lyabaauctionapp.data.model.BidHistoryItem
import com.example.lyabaauctionapp.util.formatBid

@Composable
fun BidHistory(items: List<BidHistoryItem>, currentUserId: String, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        Text("Bid History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            items(items) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.displayName,
                        fontWeight = if (item.userId == currentUserId) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.userId == currentUserId) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(item.amount.formatBid(), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
        }
    }
}
