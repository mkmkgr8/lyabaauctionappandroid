package com.example.lyabaauctionapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.lyabaauctionapp.ui.auth.AuthScreen
import com.example.lyabaauctionapp.ui.auction.AuctionScreen
import com.example.lyabaauctionapp.ui.home.HomeScreen
import com.example.lyabaauctionapp.ui.lobby.LobbyScreen
import com.example.lyabaauctionapp.ui.results.ResultsScreen
import com.example.lyabaauctionapp.ui.squad.MySquadScreen

private const val AUTH = "auth"
private const val HOME = "home"
private const val LOBBY = "lobby/{roomId}"
private const val AUCTION = "auction/{roomId}"
private const val RESULTS = "results/{roomId}"
private const val MY_SQUAD = "squad/{roomId}"

private val roomIdArg = listOf(navArgument("roomId") { type = NavType.StringType })

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AUTH) {

        composable(AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(HOME) { popUpTo(AUTH) { inclusive = true } }
                }
            )
        }

        composable(HOME) {
            HomeScreen(
                onRoomReady = { roomId ->
                    navController.navigate("lobby/$roomId")
                }
            )
        }

        composable(LOBBY, roomIdArg) {
            LobbyScreen(
                onAuctionStarted = { roomId ->
                    navController.navigate("auction/$roomId") {
                        popUpTo("lobby/$roomId") { inclusive = true }
                    }
                }
            )
        }

        composable(AUCTION, roomIdArg) {
            AuctionScreen(
                onAuctionComplete = { roomId ->
                    navController.navigate("results/$roomId") {
                        popUpTo("auction/$roomId") { inclusive = true }
                    }
                }
            )
        }

        composable(RESULTS, roomIdArg) {
            ResultsScreen(
                onGoHome = {
                    navController.navigate(HOME) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(MY_SQUAD, roomIdArg) {
            MySquadScreen(onBack = { navController.popBackStack() })
        }
    }
}
