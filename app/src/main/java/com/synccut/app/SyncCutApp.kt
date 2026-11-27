package com.synccut.app

import android.app.Application
import com.synccut.app.data.local.SyncCutDatabase
import com.synccut.app.data.local.repository.ProjectRepository

class SyncCutApp : Application() {

    lateinit var database: SyncCutDatabase
        private set

    lateinit var repository: ProjectRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = SyncCutDatabase.getInstance(this)
        repository = ProjectRepository(database)
    }
}
