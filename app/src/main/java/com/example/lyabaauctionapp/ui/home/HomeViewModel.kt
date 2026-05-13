package com.example.lyabaauctionapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyabaauctionapp.data.model.Room
import com.example.lyabaauctionapp.data.repository.AuthRepository
import com.example.lyabaauctionapp.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdRoom: Room? = null,
    val joinedRoom: Room? = null,
    val recentRooms: List<Room> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recent = roomRepository.getRecentRooms()
            _state.update { it.copy(recentRooms = recent) }
        }
    }

    fun createRoom(startingBudget: Long, minIncrement: Long, timerDuration: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val room = roomRepository.createRoom(startingBudget, minIncrement, timerDuration)
                _state.update { it.copy(isLoading = false, createdRoom = room) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to create room") }
            }
        }
    }

    fun joinRoom(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val room = roomRepository.joinRoom(code.uppercase())
                _state.update { it.copy(isLoading = false, joinedRoom = room) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Room not found") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun clearNavigation() {
        _state.update { it.copy(createdRoom = null, joinedRoom = null) }
    }
}
