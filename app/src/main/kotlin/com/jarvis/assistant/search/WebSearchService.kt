package com.jarvis.assistant.search

import com.jarvis.assistant.data.local.prefs.SecurePrefs

/**
 * Legacy compatibility layer.
 *
 * Web search is now performed directly by OpenAI Responses API
 * using the built-in web_search tool.
 *
 * This class remains so existing ViewModel/Service wiring does not
 * break, but it no longer calls Brave Search or requires a Brave key.
 */

fun needsWebSearch(text: String): Boolean {
    /*
     * The Responses API decides itself whether a web search is
     * necessary. This prevents duplicate Brave/OpenAI searches.
     */
    return false
}

class WebSearchService(
    private val prefs: SecurePrefs
) {
    suspend fun search(query: String): Result<String> {
        return Result.success("")
    }
}
