package com.cleanguard.app

import android.app.Application
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.di.DefaultAppContainer

class CleanGuardApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(applicationContext) }
}
