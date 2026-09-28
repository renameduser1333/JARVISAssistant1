package com.jarvis.assistant.ai

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/responses")
    suspend fun createResponse(
        @Header("Authorization") authHeader: String,
        @Body request: JsonObject
    ): JsonObject
}
