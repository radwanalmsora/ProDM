package com.prodm.app

import android.app.Application
import android.content.Context
import com.prodm.app.core.DownloadCore
import com.prodm.app.data.AppDatabase
import com.prodm.app.data.SettingsRepo

class ProDMApp : Application() {
    lateinit var db: AppDatabase
    lateinit var settings: SettingsRepo
    lateinit var core: DownloadCore

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsRepo(this)
        core = DownloadCore(this, db.dao(), settings)
        core.init()
    }
}

val Context.prodm: ProDMApp get() = applicationContext as ProDMApp
