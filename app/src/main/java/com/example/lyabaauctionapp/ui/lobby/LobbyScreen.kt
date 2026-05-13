package com.example.lyabaauctionapp.ui.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    onAuctionStarted: (roomId: String) -> Unit,
    viewModel: LobbyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val room = state.room

    LaunchedEffect(state.auctionStarted) {
        if (state.auctionStarted && room != null) onAuctionStarted(room.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lobby") },
                actions = {
                    room?.code?.let { code ->
                        AssistChip(
                            onClick = {},
                            label = { Text(code, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp) },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            room?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Room Code", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it.code,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Share with friends  •  Budget: £${it.startingBudget / 1_000_000}M",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Players (${state.participants.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.participants) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName, fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingContent = {
                            if (user.id == room?.createdBy)
                                AssistChip(onClick = {}, label = { Text("Host") })
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))

            val isHost = room?.createdBy == state.currentUserId

            if (isHost) {
                Button(
                    onClick = { viewModel.startAuction() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = state.participants.size >= 2
                ) { Text("Start Auction", fontSize = 16.sp) }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for host to start…")
                    }
                }
            }
        }
    }
}
