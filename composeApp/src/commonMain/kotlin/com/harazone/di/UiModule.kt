package com.harazone.di

import com.harazone.domain.service.AreaContextFactory
import com.harazone.ui.map.ChatViewModel
import com.harazone.ui.map.MapViewModel
import com.harazone.ui.profile.ProfileViewModel
import com.harazone.ui.saved.SavedPlacesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    factory { AreaContextFactory(get(), get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get(), get(), get()) }
    viewModel { SavedPlacesViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
}
