package com.areadiscovery.di

import org.koin.core.module.Module

expect fun platformModule(): Module

fun appModule() = listOf(dataModule, uiModule, platformModule())
