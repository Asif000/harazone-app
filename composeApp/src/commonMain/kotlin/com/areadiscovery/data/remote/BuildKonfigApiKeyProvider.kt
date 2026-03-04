package com.areadiscovery.data.remote

import com.areadiscovery.BuildKonfig
import com.areadiscovery.domain.provider.ApiKeyProvider

internal class BuildKonfigApiKeyProvider : ApiKeyProvider {
    override val geminiApiKey: String = BuildKonfig.GEMINI_API_KEY
}
