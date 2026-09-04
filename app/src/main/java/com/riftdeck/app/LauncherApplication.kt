package com.riftdeck.app

import android.app.Application
import com.riftdeck.data.repository.MockGameRepository
import com.riftdeck.domain.repository.GameRepository

class LauncherApplication : Application() {
    val gameRepository: GameRepository by lazy(::MockGameRepository)
}

