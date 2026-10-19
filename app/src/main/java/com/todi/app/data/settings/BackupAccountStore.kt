package com.todi.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.todi.app.domain.model.BackupAccount
import com.todi.app.domain.repository.BackupAccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "todi_auth")

@Singleton
class BackupAccountStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupAccountRepository {
        override val account: Flow<BackupAccount?> =
            context.authDataStore.data.map { prefs ->
                val email = prefs[KEY_EMAIL] ?: return@map null
                val token = prefs[KEY_TOKEN] ?: return@map null
                BackupAccount(
                    email = email,
                    token = token,
                    expiresAt = prefs[KEY_EXPIRES]?.let(Instant::ofEpochMilli),
                )
            }

        override suspend fun lastUploadedDigest(): String? = context.authDataStore.data.first()[KEY_DIGEST]

        override suspend fun rememberUploadedDigest(digest: String) {
            context.authDataStore.edit { it[KEY_DIGEST] = digest }
        }

        override suspend fun signIn(
            email: String,
            token: String,
            expiresAt: Instant?,
        ) {
            context.authDataStore.edit { prefs ->
                prefs[KEY_EMAIL] = email
                prefs[KEY_TOKEN] = token

                if (expiresAt == null) prefs.remove(KEY_EXPIRES) else prefs[KEY_EXPIRES] = expiresAt.toEpochMilli()

                prefs.remove(KEY_DIGEST)
            }
        }

        override suspend fun signOut() {
            context.authDataStore.edit { it.clear() }
        }

        private companion object {
            val KEY_EMAIL = stringPreferencesKey("email")
            val KEY_TOKEN = stringPreferencesKey("token")
            val KEY_EXPIRES = longPreferencesKey("expires_at_millis")

            val KEY_DIGEST = stringPreferencesKey("last_uploaded_digest")
        }
    }
