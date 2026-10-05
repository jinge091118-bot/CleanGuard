package com.cleanguard.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.cleanGuardPreferences by preferencesDataStore(
    name = "cleanguard_preferences",
)
