package com.example.lyabaauctionapp.ui.squad

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyabaauctionapp.data.model.Player
import com.example.lyabaauctionapp.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MySquadUiState(
    val isLoading: Boolean = true,
    val players: List<Player> = emptyList(),
    val remainingBudget: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class MySquadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _state = MutableStateFlow(MySquadUiState())
    val state: StateFlow<MySquadUiState> = _state.asStateFlow()

    init {
        loadSquad()
    }

    private fun loadSquad() {
        viewModelScope.launch {
            try {
                val all = playerRepository.getPlayers()
                val myPlayers = all.filter { it.ownedBy != null }
                _state.update { it.copy(isLoading = false, players = myPlayers) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
