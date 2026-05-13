package com.example.lyabaauctionapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onRoomReady: (roomId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdRoom, state.joinedRoom) {
        state.createdRoom?.id?.let { id -> onRoomReady(id); viewModel.clearNavigation() }
        state.joinedRoom?.id?.let { id -> onRoomReady(id); viewModel.clearNavigation() }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            isLoading = state.isLoading,
            onDismiss = { showCreateDialog = false },
            onCreate = { budget, increment, timer ->
                viewModel.createRoom(budget, increment, timer)
                showCreateDialog = false
            }
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            isLoading = state.isLoading,
            error = state.error,
            onDismiss = { showJoinDialog = false },
            onJoin = { code -> viewModel.joinRoom(code) }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Lyaba Auction", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Live Football Player Auction", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(56.dp))

        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Create Room", fontSize = 16.sp) }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showJoinDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Join Room", fontSize = 16.sp) }
    }
}

@Composable
private fun CreateRoomDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (budget: Long, increment: Long, timer: Int) -> Unit
) {
    var budget by remember { mutableStateOf("100000000") }
    var increment by remember { mutableStateOf("1000000") }
    var timer by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter(Char::isDigit) },
                    label = { Text("Starting Budget (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = increment,
                    onValueChange = { increment = it.filter(Char::isDigit) },
                    label = { Text("Min Increment (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = timer,
                    onValueChange = { timer = it.filter(Char::isDigit).take(3) },
                    label = { Text("Timer (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            val valid = budget.isNotBlank() && increment.isNotBlank() && timer.isNotBlank()
            Button(
                onClick = {
                    onCreate(
                        budget.toLongOrNull() ?: 100_000_000L,
                        increment.toLongOrNull() ?: 1_000_000L,
                        timer.toIntOrNull() ?: 10
                    )
                },
                enabled = valid && !isLoading
            ) { if (isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JoinRoomDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text("Room Code") },
                    placeholder = { Text("6-character code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 6 && !isLoading
            ) { if (isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
