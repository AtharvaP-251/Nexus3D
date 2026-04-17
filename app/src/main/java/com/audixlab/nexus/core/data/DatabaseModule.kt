package com.audixlab.nexus.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexusDatabase {
        return Room.databaseBuilder(
            context,
            NexusDatabase::class.java,
            "nexus_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTrackDao(database: NexusDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    fun providePresetDao(database: NexusDatabase): PresetDao {
        return database.presetDao()
    }

    @Provides
    fun provideSettingsDao(database: NexusDatabase): SettingsDao {
        return database.settingsDao()
    }
}
