package com.uniccomj.jivana.di

import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JiveModule {
    @Provides
    @Singleton
    fun provideHabitCompletionEvents(): HabitCompletionEvents = HabitCompletionEvents()

    @Provides
    fun provideJiveScoringConfig(): JiveScoringConfig = JiveScoringConfig()

    @Provides
    fun provideJiveConditionScorer(config: JiveScoringConfig): JiveConditionScorer =
        JiveConditionScorer(config)

    @Provides
    fun provideObserveJiveConditionUseCase(
        repository: HabitHistoryRepository,
        scorer: JiveConditionScorer,
        ensureHabitSchedules: EnsureHabitSchedulesUseCase
    ): ObserveJiveConditionUseCase =
        ObserveJiveConditionUseCase(repository, scorer, ensureHabitSchedules)

    @Provides
    fun provideClock(): Clock = object : Clock() {
        // Resolve the current device zone on every read, including after travel or a settings change.
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun instant(): Instant = Instant.now()
        override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    }
}
