package com.areadiscovery.di

import com.areadiscovery.ui.summary.SummaryStateMapper
import com.areadiscovery.ui.summary.SummaryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    factory { SummaryStateMapper() }
    viewModel { SummaryViewModel(get(), get(), get()) }
}
