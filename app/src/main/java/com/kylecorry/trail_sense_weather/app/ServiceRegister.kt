package com.kylecorry.trail_sense_weather.app

import android.content.Context
import com.kylecorry.andromeda.core.cache.AppServiceRegistry
import com.kylecorry.trail_sense_weather.infrastructure.persistence.AppDatabase
import com.kylecorry.trail_sense_weather.ui.FormatService

object ServiceRegister {
    fun setup(context: Context) {
        val appContext = context.applicationContext

        // Shared services
        AppServiceRegistry.register(FormatService.getInstance(appContext))
        AppServiceRegistry.register(AppDatabase.getInstance(appContext))
    }
}