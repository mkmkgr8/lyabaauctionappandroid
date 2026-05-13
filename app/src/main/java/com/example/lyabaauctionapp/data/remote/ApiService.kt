package com.example.lyabaauctionapp.data.remote

import com.example.lyabaauctionapp.data.model.*
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun getMe(): User

    // Rooms
    @POST("rooms")
    suspend fun createRoom(@Body request: CreateRoomRequest): Room

    @POST("rooms/{code}/join")
    suspend fun joinRoom(@Path("code") code: String): Room

    @GET("rooms/{id}")
    suspend fun getRoom(@Path("id") id: String): Room

    @POST("rooms/{id}/start")
    suspend fun startAuction(@Path("id") id: String)

    @GET("rooms/{id}/results")
    suspend fun getResults(@Path("id") id: String): List<AuctionResult>

    @POST("rooms/{id}/bid")
    suspend fun placeBid(@Path("id") roomId: String, @Body request: BidRequest)

    @POST("rooms/{id}/nominate")
    suspend fun nominatePlayer(@Path("id") roomId: String, @Body request: NominateRequest)

    // Players
    @GET("players")
    suspend fun getPlayers(): List<Player>

    @GET("players/{id}")
    suspend fun getPlayer(@Path("id") id: String): Player

    // Server time sync
    @GET("time")
    suspend fun getServerTime(): ServerTimeResponse
}
