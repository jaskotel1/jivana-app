package com.uniccomj.jivana.di

import android.content.Context
import androidx.room.Room
import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.data.local.database.JivanaDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): JivanaDatabase =
        Room.databaseBuilder(
            context,
            JivanaDatabase::class.java,
            "jivana.db"
        ).build()

    @Provides
    fun provideHabitHistoryDao(database: JivanaDatabase): HabitHistoryDao =
        database.habitHistoryDao()
}
