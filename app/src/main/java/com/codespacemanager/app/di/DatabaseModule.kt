package com.codespacemanager.app.di

import android.content.Context
import androidx.room.Room
import com.codespacemanager.app.data.local.db.AccountDao
import com.codespacemanager.app.data.local.db.AppDatabase
import com.codespacemanager.app.data.local.db.CodespaceDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "codespace-manager.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCodespaceDao(database: AppDatabase): CodespaceDao = database.codespaceDao()
}
