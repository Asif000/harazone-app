package com.harazone.domain.provider

import android.content.Context
import android.text.TextUtils
import android.view.View
import java.util.Currency
import java.util.Locale

class AndroidLocaleProvider(private val context: Context) : LocaleProvider {
    override val languageTag: String
        get() = Locale.getDefault().toLanguageTag()
    override val isRtl: Boolean
        get() = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
    override val homeCurrencyCode: String
        get() = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (e: Exception) { "USD" }
}
