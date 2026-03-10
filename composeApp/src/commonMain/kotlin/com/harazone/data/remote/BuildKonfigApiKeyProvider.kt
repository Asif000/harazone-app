package com.harazone.data.remote

import com.harazone.BuildKonfig
import com.harazone.domain.provider.ApiKeyProvider

internal class BuildKonfigApiKeyProvider : ApiKeyProvider {
    override val geminiApiKey: String = BuildKonfig.GEMINI_API_KEY
}
