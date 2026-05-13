package com.example.lyabaauctionapp.ui.auction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lyabaauctionapp.ui.auction.components.*
import com.example.lyabaauctionapp.util.formatBudget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionScreen(
    onAuctionComplete: (roomId: String) -> Unit,
    viewModel: AuctionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate to results when auction completes
    LaunchedEffect(state.auctionComplete) {
        if (state.auctionComplete) {
            // roomId comes from SavedStateHandle inside the ViewModel
        }
    }

    // Show bid rejection snackbar
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    // Sold banner
    state.soldBanner?.let { msg ->
        SoldBanner(message = msg, onDismiss = { viewModel.dismissSoldBanner() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Auction") },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            state.currentUserBudget.formatBudget(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val auction = state.activeAuction

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (auction != null) {
                // Player card
                PlayerCard(player = auction.player)

                // Bid amount and bidder
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CountdownTimer(seconds = state.timerSeconds)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Current Bid", style = MaterialTheme.typography.labelMedium)
                                Text(auction.currentBid.formatBudget(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Highest Bidder", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    auction.currentBidderName.ifEmpty { "No bids yet" },
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (auction.currentBidder == state.currentUserId)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Bid controls
                BidControls(
                    currentBid = auction.currentBid,
                    minIncrement = 1_000_000L,
                    myBudget = state.currentUserBudget,
                    iAmHighestBidder = auction.currentBidder == state.currentUserId && state.currentUserId.isNotEmpty(),
                    isBidding = state.isBidding,
                    onBid = { viewModel.placeBid(it) }
                )

                // Bid history
                BidHistory(
                    items = state.bidHistory,
                    currentUserId = state.currentUserId
                )

            } else if (state.isNominationOpen) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nomination Open", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Select a player from the player list to nominate", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Waiting for auction to start…")
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
