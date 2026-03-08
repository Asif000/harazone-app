package com.areadiscovery.di

import com.areadiscovery.domain.service.AreaContextFactory
import com.areadiscovery.ui.map.ChatViewModel
import com.areadiscovery.ui.map.MapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    factory { AreaContextFactory(get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get()) }
}
