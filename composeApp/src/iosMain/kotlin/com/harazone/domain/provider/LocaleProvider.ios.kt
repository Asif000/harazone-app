package com.harazone.domain.provider

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.currentLocale
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.currencyCode

class IosLocaleProvider : LocaleProvider {
    override val languageTag: String
        get() = NSLocale.currentLocale.localeIdentifier
            .substringBefore("@")
            .replace("_", "-")
    override val isRtl: Boolean
        get() {
            val lang = NSLocale.currentLocale.languageCode ?: return false
            return NSLocale.characterDirectionForLanguage(lang) == NSLocaleLanguageDirectionRightToLeft
        }
    override val homeCurrencyCode: String
        get() = NSLocale.currentLocale.currencyCode ?: "USD"
}
