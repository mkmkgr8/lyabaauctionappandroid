package com.example.lyabaauctionapp.ui.auction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyabaauctionapp.data.model.*
import com.example.lyabaauctionapp.data.repository.AuctionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuctionUiState(
    val currentUserId: String = "",
    val currentUserBudget: Long = 0L,
    val activeAuction: ActiveAuction? = null,
    val bidHistory: List<BidHistoryItem> = emptyList(),
    val timerSeconds: Int = 0,
    val soldBanner: String? = null,
    val isNominationOpen: Boolean = false,
    val auctionComplete: Boolean = false,
    val isBidding: Boolean = false,   // true while REST bid call is in-flight
    val snackbar: String? = null
)

@HiltViewModel
class AuctionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auctionRepository: AuctionRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _state = MutableStateFlow(AuctionUiState())
    val state: StateFlow<AuctionUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        connectAndObserve()
    }

    private fun connectAndObserve() {
        viewModelScope.launch {
            auctionRepository.connectToRoom(roomId)
            auctionRepository.events.collect { event ->
                when (event) {
                    is AuctionEvent.RoomJoined -> {
                        event.currentState?.let { startTimer(it.timerEndsAt) }
                        _state.update { it.copy(activeAuction = event.currentState) }
                    }
                    is AuctionEvent.AuctionStarted -> {
                        _state.update { it.copy(activeAuction = event.auction, isNominationOpen = false, soldBanner = null) }
                        startTimer(event.auction.timerEndsAt)
                    }
                    is AuctionEvent.NewBid -> {
                        val item = BidHistoryItem(event.userId, event.displayName, event.amount, System.currentTimeMillis())
                        _state.update { s ->
                            s.copy(
                                activeAuction = s.activeAuction?.copy(
                                    currentBid = event.amount,
                                    currentBidder = event.userId,
                                    currentBidderName = event.displayName,
                                    timerEndsAt = event.timerEndsAt
                                ),
                                bidHistory = listOf(item) + s.bidHistory,
                                isBidding = false   // server confirmed — clear in-flight flag
                            )
                        }
                        startTimer(event.timerEndsAt)
                    }
                    is AuctionEvent.BidRejected -> {
                        _state.update { it.copy(snackbar = event.reason) }
                    }
                    is AuctionEvent.PlayerSold -> {
                        timerJob?.cancel()
                        _state.update { it.copy(
                            activeAuction = null,
                            timerSeconds = 0,
                            soldBanner = "${event.winnerName} won ${_state.value.activeAuction?.player?.name ?: "player"} for £${event.amount / 1_000_000}M"
                        ) }
                    }
                    is AuctionEvent.BudgetUpdated -> {
                        if (event.userId == _state.value.currentUserId) {
                            _state.update { it.copy(currentUserBudget = event.newBudget) }
                        }
                    }
                    is AuctionEvent.NominationOpen -> {
                        _state.update { it.copy(isNominationOpen = true, soldBanner = null) }
                    }
                    is AuctionEvent.AuctionComplete -> {
                        _state.update { it.copy(auctionComplete = true) }
                    }
                    is AuctionEvent.Error -> {
                        _state.update { it.copy(snackbar = event.message) }
                    }
                }
            }
        }
    }

    private fun startTimer(timerEndsAt: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = auctionRepository.remainingSeconds(timerEndsAt)
                _state.update { it.copy(timerSeconds = remaining) }
                if (remaining <= 0) break
                delay(100)
            }
        }
    }

    fun placeBid(amount: Long) {
        if (_state.value.isBidding) return
        viewModelScope.launch {
            _state.update { it.copy(isBidding = true, snackbar = null) }
            try {
                auctionRepository.placeBid(roomId, amount)
                // isBidding stays true — cleared only when server broadcasts new_bid back
            } catch (e: Exception) {
                // Network failure or server rejection (4xx) — flip flag and show reason
                _state.update { it.copy(isBidding = false, snackbar = e.message ?: "Bid failed") }
            }
        }
    }

    fun nominatePlayer(playerId: String, baseBid: Long) {
        viewModelScope.launch {
            try {
                auctionRepository.nominatePlayer(roomId, playerId, baseBid)
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "Nomination failed") }
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun dismissSoldBanner() = _state.update { it.copy(soldBanner = null) }

    override fun onCleared() {
        super.onCleared()
        auctionRepository.disconnectFromRoom()
    }
}
