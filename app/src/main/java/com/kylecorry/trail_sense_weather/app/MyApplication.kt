package com.kylecorry.trail_sense_weather.app

import android.app.Application
import com.kylecorry.andromeda.preferences.PreferenceMigration
import com.kylecorry.andromeda.preferences.PreferenceMigrator

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceRegister.setup(this)
        NotificationChannels.createChannels(this)
        migratePreferences()
    }

    private fun migratePreferences() {
        val key = "pref_version"
        val version = 0
        val migrations = listOf<PreferenceMigration>()
        PreferenceMigrator(this, key).migrate(version, migrations)
    }
}