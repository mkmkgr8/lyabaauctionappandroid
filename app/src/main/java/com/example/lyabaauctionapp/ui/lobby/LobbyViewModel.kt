package com.example.lyabaauctionapp.ui.lobby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyabaauctionapp.data.model.AuctionEvent
import com.example.lyabaauctionapp.data.model.Room
import com.example.lyabaauctionapp.data.model.User
import com.example.lyabaauctionapp.data.repository.AuctionRepository
import com.example.lyabaauctionapp.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LobbyUiState(
    val isLoading: Boolean = true,
    val room: Room? = null,
    val participants: List<User> = emptyList(),
    val currentUserId: String = "",
    val auctionStarted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LobbyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepository: RoomRepository,
    private val auctionRepository: AuctionRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _state = MutableStateFlow(LobbyUiState())
    val state: StateFlow<LobbyUiState> = _state.asStateFlow()

    init {
        loadRoom()
        connectAndObserve()
    }

    private fun loadRoom() {
        viewModelScope.launch {
            try {
                val room = roomRepository.getRoom(roomId)
                _state.update { it.copy(room = room, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun connectAndObserve() {
        viewModelScope.launch {
            auctionRepository.connectToRoom(roomId)
            auctionRepository.events.collect { event ->
                when (event) {
                    is AuctionEvent.RoomJoined -> {
                        _state.update { it.copy(participants = event.users) }
                    }
                    is AuctionEvent.AuctionStarted -> {
                        _state.update { it.copy(auctionStarted = true) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startAuction() {
        viewModelScope.launch {
            try {
                roomRepository.startAuction(roomId)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auctionRepository.disconnectFromRoom()
    }
}
