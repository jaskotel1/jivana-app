package com.uniccomj.jivana.di

import com.uniccomj.jivana.domain.model.HabitPausePolicy
import com.uniccomj.jivana.domain.repository.HabitRepository
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object HabitModule {
    @Provides
    fun provideHabitPausePolicy(): HabitPausePolicy = HabitPausePolicy.Default

    @Provides
    fun provideCreateHabit(repository: HabitRepository, clock: Clock): CreateHabitUseCase =
        CreateHabitUseCase(repository, clock)

    @Provides
    fun provideEnsureHabitSchedules(
        repository: HabitRepository,
        clock: Clock
    ): EnsureHabitSchedulesUseCase = EnsureHabitSchedulesUseCase(repository, clock)
}
