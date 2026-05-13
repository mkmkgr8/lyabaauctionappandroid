package com.example.lyabaauctionapp.data.repository

import com.example.lyabaauctionapp.data.local.TokenManager
import com.example.lyabaauctionapp.data.model.AuthResponse
import com.example.lyabaauctionapp.data.model.LoginRequest
import com.example.lyabaauctionapp.data.model.RegisterRequest
import com.example.lyabaauctionapp.data.model.User
import com.example.lyabaauctionapp.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): AuthResponse {
        val response = api.login(LoginRequest(email, password))
        tokenManager.saveToken(response.token)
        return response
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResponse {
        val response = api.register(RegisterRequest(email, password, displayName))
        tokenManager.saveToken(response.token)
        return response
    }

    suspend fun getMe(): User = api.getMe()

    suspend fun logout() = tokenManager.clearToken()

    suspend fun isLoggedIn(): Boolean = tokenManager.getToken() != null
}
