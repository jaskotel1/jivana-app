package com.uniccomj.jivana.di

import com.uniccomj.jivana.data.repository.RoomHabitHistoryRepository
import com.uniccomj.jivana.data.repository.RoomHabitRepository
import com.uniccomj.jivana.domain.repository.HabitCycleRepository
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitRepository
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
    abstract fun bindHabitRepository(repository: RoomHabitRepository): HabitRepository

    @Binds
    abstract fun bindHabitCycleRepository(repository: RoomHabitRepository): HabitCycleRepository

    @Binds
    @Singleton
    abstract fun bindHabitHistoryRepository(
        repository: RoomHabitHistoryRepository
    ): HabitHistoryRepository
}
