package com.cleanguard.app.platform.intents

import android.content.Intent
import android.net.Uri
import android.provider.Settings

class AppSettingsIntentFactory {
    fun createUsageAccessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun createApplicationDetailsSettings(packageName: String): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
}
