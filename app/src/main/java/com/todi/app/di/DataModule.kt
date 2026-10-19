package com.todi.app.di

import android.content.Context
import androidx.room.Room
import com.todi.app.backup.BackupTransport
import com.todi.app.backup.HttpBackupTransport
import com.todi.app.data.TaskRepositoryImpl
import com.todi.app.data.local.AppDatabase
import com.todi.app.data.local.TaskDao
import com.todi.app.data.settings.BackupAccountStore
import com.todi.app.data.settings.SettingsDataStore
import com.todi.app.domain.repository.BackupAccountRepository
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.SystemTodayProvider
import com.todi.app.domain.util.TodayProvider
import com.todi.app.presentation.voice.AndroidVoiceEnvironment
import com.todi.app.presentation.voice.SpeechEngineFactory
import com.todi.app.presentation.voice.VoiceEnvironment
import com.todi.app.presentation.voice.VoskSpeechEngineFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupAccountRepository(impl: BackupAccountStore): BackupAccountRepository

    @Binds
    @Singleton
    abstract fun bindBackupTransport(impl: HttpBackupTransport): BackupTransport

    @Binds
    @Singleton
    abstract fun bindTodayProvider(impl: SystemTodayProvider): TodayProvider

    @Binds
    @Singleton
    abstract fun bindVoiceEnvironment(impl: AndroidVoiceEnvironment): VoiceEnvironment

    @Binds
    @Singleton
    abstract fun bindSpeechEngineFactory(impl: VoskSpeechEngineFactory): SpeechEngineFactory

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
        ): AppDatabase =
            Room
                .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                ).build()

        @Provides
        fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()
    }
}
