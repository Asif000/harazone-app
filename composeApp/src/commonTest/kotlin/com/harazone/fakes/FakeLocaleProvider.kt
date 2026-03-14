package com.harazone.fakes

import com.harazone.domain.provider.LocaleProvider

class FakeLocaleProvider(
    override val languageTag: String = "en",
    override val isRtl: Boolean = false,
    override val homeCurrencyCode: String = "USD",
) : LocaleProvider
