package com.uniccomj.jivana.di

import com.uniccomj.jivana.data.repository.RoomHabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindHabitHistoryRepository(
        repository: RoomHabitHistoryRepository
    ): HabitHistoryRepository
}
