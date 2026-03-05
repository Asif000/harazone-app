package com.areadiscovery.di

import com.areadiscovery.domain.service.AreaContextFactory
import com.areadiscovery.ui.map.MapViewModel
import com.areadiscovery.ui.summary.SummaryStateMapper
import com.areadiscovery.ui.summary.SummaryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    factory { SummaryStateMapper() }
    factory { AreaContextFactory(get()) }
    viewModel { SummaryViewModel(get(), get(), get(), get(), get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get()) }
}
