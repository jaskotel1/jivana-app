package com.uniccomj.jivana.di

import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object JiveModule {
    @Provides
    fun provideJiveConditionScorer(): JiveConditionScorer = JiveConditionScorer()

    @Provides
    fun provideObserveJiveConditionUseCase(
        repository: HabitHistoryRepository,
        scorer: JiveConditionScorer
    ): ObserveJiveConditionUseCase = ObserveJiveConditionUseCase(repository, scorer)

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
