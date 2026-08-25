package com.github.ShinkaiKung.verbalkiller

import android.app.Application
import com.github.ShinkaiKung.verbalkiller.logic.persistence.GroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VerbalKillerApplication : Application() {
    val repository: GroupRepository by lazy { GroupRepository.getInstance(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository.initializeIn(applicationScope)
    }
}
