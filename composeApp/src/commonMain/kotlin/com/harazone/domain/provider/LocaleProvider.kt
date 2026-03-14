package com.harazone.domain.provider

interface LocaleProvider {
    val languageTag: String       // e.g. "pt-BR", "en", "ar"
    val isRtl: Boolean
    val homeCurrencyCode: String  // e.g. "BRL", "USD"
}
