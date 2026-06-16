package com.joyproxy.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "proxy_settings")
