package com.jarvis.assistant.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jarvis.assistant.data.local.prefs.SecurePrefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * JARVIS OpenAI Responses API client.
 *
 * Uses the Responses API with the built-in web_search tool.
 * The API key remains on-device in SecurePrefs.
 */
class OpenAiService(private val prefs: SecurePrefs) : AiService {

    override suspend fun send(
        history: List<ChatMessage>,
        systemPrompt: String
    ): Result<AiResult> {

        val apiKey = prefs.aiApiKey

        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(
                    "No AI API key configured. Add one in Settings."
                )
            )
        }

        val baseUrl = prefs.aiBaseUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://api.openai.com/"

        return try {
            val api = buildApi(baseUrl)

            /*
             * Keep the conversation compact enough for voice use.
             * The current user message is already included in history.
             */
            val conversation = history
                .takeLast(20)
                .joinToString("\n\n") { message ->
                    val role = when (message.role.lowercase()) {
                        "assistant" -> "JARVIS"
                        "system" -> "SYSTEM"
                        else -> "USER"
                    }

                    "$role: ${message.content}"
                }

            val input = if (conversation.isBlank()) {
                "USER: Please answer the user's request."
            } else {
                conversation
            }

            val request = JsonObject().apply {
                addProperty("model", prefs.aiModel)

                addProperty("instructions", systemPrompt)

                addProperty("input", input)

                add(
                    "tools",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("type", "web_search")
                                addProperty(
                                    "search_context_size",
                                    "medium"
                                )
                            }
                        )
                    }
                )

                /*
                 * Give research questions enough room for a useful answer.
                 * This is an output limit, not a web-search limit.
                 */
                addProperty("max_output_tokens", 3000)
            }

            val response = api.createResponse(
                authHeader = "Bearer $apiKey",
                request = request
            )

            val rawText = extractOutputText(response)

            if (rawText.isBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "OpenAI returned an empty response."
                    )
                )
            }

            Result.success(extractCommand(rawText))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildApi(baseUrl: String): OpenAiApi {

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)

            /*
             * Research may legitimately take longer than a normal
             * chat request, but it must never hang forever.
             */
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()

        val normalizedBase =
            if (baseUrl.endsWith("/")) {
                baseUrl
            } else {
                "$baseUrl/"
            }

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    /**
     * Responses API can return multiple output items:
     * web_search_call, message, reasoning, etc.
     *
     * Never assume output[0] is the answer.
     */
    private fun extractOutputText(response: JsonObject): String {

        val output = response
            .getAsJsonArray("output")
            ?: return response
                .get("output_text")
                ?.asString
                .orEmpty()

        val texts = mutableListOf<String>()

        for (itemElement in output) {

            if (!itemElement.isJsonObject) continue

            val item = itemElement.asJsonObject

            if (item.get("type")?.asString != "message") {
                continue
            }

            val content = item
                .getAsJsonArray("content")
                ?: continue

            for (contentElement in content) {

                if (!contentElement.isJsonObject) continue

                val contentItem = contentElement.asJsonObject

                if (
                    contentItem.get("type")?.asString ==
                    "output_text"
                ) {
                    val text = contentItem
                        .get("text")
                        ?.asString
                        .orEmpty()

                    if (text.isNotBlank()) {
                        texts += text
                    }
                }
            }
        }

        return texts.joinToString("\n\n").trim()
    }

    private fun extractCommand(rawText: String): AiResult {

        val regex = Regex(
            "```jarvis_command\\\\s*([\\\\s\\\\S]*?)```"
        )

        val match = regex.find(rawText)

        val commandJson =
            match?.groupValues?.get(1)?.trim()

        val replyText =
            if (match != null) {
                rawText
                    .replace(match.value, "")
                    .trim()
            } else {
                rawText.trim()
            }

        return AiResult(
            replyText = replyText,
            commandJson = commandJson
        )
    }
}
