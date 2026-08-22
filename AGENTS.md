# Jivana Project Guidelines

## Project overview

Jivana is an Android application focused on helping users build healthier long-term lifestyle habits through gamification and a visual avatar system.

The avatar reflects the user's lifestyle. It should gradually change based on repeated behavioral patterns rather than individual actions.

Core product principles:

* Consistency is more important than perfection.
* Long-term patterns matter more than individual mistakes.
* The application should encourage rather than punish.
* One failed day must not destroy long-term progress.
* Avatar changes should happen gradually.
* The avatar represents the consequences of the user's lifestyle rather than being a virtual pet that needs to be cared for.
* Jivana should feel like a lifestyle simulation supported by habit tracking rather than a traditional habit tracker.

## Technology stack

Use:

* Kotlin
* Jetpack Compose
* Material 3
* MVVM
* Kotlin Coroutines
* Kotlin Flow and StateFlow
* Room Database
* Hilt for dependency injection
* Navigation Compose
* JUnit for unit tests
* Compose UI Tests
* Gradle

Do not introduce additional frameworks or libraries unless they provide clear value and are necessary for the requested functionality.

Do not introduce Firebase, a custom backend, or remote persistence unless explicitly requested.

## Architecture

Use:

MVVM + Repository Pattern + Use Cases

Keep the project as a single Android `app` module for now.

Do not introduce a multi-module architecture unless explicitly requested.

The expected application flow is:

Compose UI
→ ViewModel
→ Use Case
→ Domain Repository Interface
→ Repository Implementation
→ Room

Data should flow back through these layers to the UI.

## Package structure

Use the following base structure:

`data/local/dao`
`data/local/database`
`data/local/entity`
`data/mapper`
`data/repository`

`domain/model`
`domain/repository`
`domain/usecase`

`ui/onboarding`
`ui/home`
`ui/habits`
`ui/checkin`
`ui/avatar`
`ui/progress`
`ui/settings`
`ui/components`

`navigation`
`di`
`util`

Do not create empty packages just to satisfy this structure. Create packages when they are actually needed.

## Domain layer

The domain layer contains business rules.

It should remain independent from Android UI and persistence implementation details wherever reasonably possible.

The domain layer must not depend directly on:

* Jetpack Compose
* Room entities
* Activity
* Fragment
* Android UI components

Examples of domain models may include:

* Habit
* HabitEntry
* AvatarState
* UserProgress
* Achievement

Room entities must not be used directly as domain models.

## Data layer

The data layer is responsible for persistence and data transformation.

Room entities belong under:

`data/local/entity`

DAOs belong under:

`data/local/dao`

Database configuration belongs under:

`data/local/database`

Repository implementations belong under:

`data/repository`

Use mappers to convert between persistence models and domain models when appropriate.

Do not expose Room entities directly to the UI layer.

## Repository pattern

Repository interfaces belong in:

`domain/repository`

Repository implementations belong in:

`data/repository`

ViewModels must not access Room DAOs directly.

## Use cases

Business logic should normally live in use cases.

Potential examples include:

* `CompleteDailyCheckInUseCase`
* `CalculateAvatarStateUseCase`
* `CalculateConsistencyUseCase`
* `GetActiveHabitsUseCase`
* `UnlockAchievementUseCase`

Do not create unnecessary use cases that merely forward a single trivial call.

Prefer simple architecture over unnecessary abstraction.

## Avatar system

The avatar system is a core part of Jivana.

Avatar state should reflect long-term behavioral trends.

Do not implement simplistic rules such as:

`user eats sweets once → avatar immediately becomes less healthy`

Prefer:

`repeated behavior over time → gradual change in relevant avatar state`

Potential avatar dimensions may include:

* energy
* fitness
* nutrition
* sleep
* mood

Do not assume these values must be shown numerically to users.

Whenever possible, state should also be communicated visually through the avatar, animation and environment.

## Habit philosophy

Jivana should not encourage all-or-nothing thinking.

Avoid mechanics where:

* one missed day resets meaningful progress,
* one unhealthy choice causes a large negative consequence,
* users are punished for imperfect behavior.

Prefer rolling consistency calculations such as the last 7 or 30 days.

Streaks may be introduced later, but they should not be the primary measurement of progress.

## UI architecture

Each significant feature should generally have its own package.

A feature may contain:

* Screen
* ViewModel
* UiState
* UiEvent

Example:

`ui/checkin/DailyCheckInScreen.kt`
`ui/checkin/DailyCheckInViewModel.kt`
`ui/checkin/DailyCheckInUiState.kt`
`ui/checkin/DailyCheckInUiEvent.kt`

Do not create files that are not needed by the feature.

Composable functions should primarily:

* render state,
* display UI,
* handle user interaction,
* forward events to the ViewModel.

Do not put business logic directly inside Composable functions.

## ViewModels

ViewModels should communicate with use cases rather than DAOs.

Prefer exposing immutable UI state using:

`StateFlow<UiState>`

Keep mutable state private inside the ViewModel.

Do not perform database operations directly from Composable functions.

## Navigation

Keep navigation definitions centralized under:

`navigation`

Do not scatter raw route strings throughout the project.

Use Navigation Compose.

## Dependency injection

Use Hilt for dependency injection.

Shared dependencies such as databases and repositories should be provided through dependency injection.

Do not manually instantiate repositories or databases inside ViewModels or Composable functions.

## Room

Use Room as the initial persistence solution.

Database migrations should preserve existing user data.

Do not use destructive migrations in production without an explicit reason.

## Strings and localization

All user-visible strings must use Android string resources.

Do not hardcode user-visible text directly inside Composable functions.

The architecture should allow the application to support multiple languages in the future.

## UI and design

Use Material 3.

Do not hardcode arbitrary colors throughout individual screens.

Prefer theme colors and reusable design tokens.

Create reusable UI components when actual reuse exists.

Do not build a large design system prematurely.

## Naming conventions

Follow standard Kotlin naming conventions.

Classes:
`PascalCase`

Functions and properties:
`camelCase`

Constants:
`UPPER_SNAKE_CASE`

Use descriptive names.

Avoid vague names such as:

* Utils
* Helper
* Manager
* Processor

unless the class has a clearly defined responsibility that justifies the name.

## Testing

Business logic should be covered by unit tests.

High-priority areas include:

* avatar state calculation
* consistency calculation
* daily check-in logic
* habit trend calculations
* achievements
* progress calculations

Tests should describe behavior rather than implementation details.

Prefer Given / When / Then structure where appropriate.

When adding or changing meaningful business logic, add or update relevant tests.

## UI tests

Use Compose UI Tests for important user journeys.

Prioritize testing:

* onboarding
* habit selection
* daily check-in
* navigation
* progress

Test user-visible behavior rather than internal Compose implementation details.

## Coroutines

Use structured concurrency.

Use `viewModelScope` inside ViewModels where appropriate.

Never use `GlobalScope`.

Avoid blocking calls on the main thread.

## Error handling

Do not silently ignore errors.

Avoid broad exception handling such as `catch (Exception)` unless there is a justified reason.

Errors displayed to users should be understandable and should not expose technical implementation details.

## Security

Never commit:

* passwords
* API keys
* access tokens
* signing credentials
* service account credentials
* production secrets

Do not hardcode secrets in source code.

Respect `.gitignore` and keep environment-specific secrets outside version control.

## Git workflow

The main long-lived branches are:

* `master`
* `develop`

Feature work should normally happen on dedicated branches such as:

* `feature/onboarding`
* `feature/avatar-selection`
* `feature/daily-checkin`
* `feature/habit-tracking`

Do not make unrelated changes while implementing a feature.

Keep commits focused and descriptive.

Do not commit generated build files or IDE-specific temporary files.

## Scope control

When implementing a task:

1. Read and understand the requested behavior.
2. Inspect the existing implementation before making changes.
3. Follow the existing architecture.
4. Reuse existing components where appropriate.
5. Make the smallest coherent change that fully implements the requirement.
6. Add or update relevant tests.
7. Run relevant tests and build checks.
8. Fix problems caused by the implementation.
9. Do not refactor unrelated code unless necessary.
10. Do not implement functionality that was not requested.

If a requirement is ambiguous and the ambiguity could materially affect architecture, data, or user behavior, ask for clarification instead of making a major assumption.

## MVP scope

The initial Jivana MVP should focus on:

1. onboarding,
2. avatar selection,
3. selecting a small number of habits,
4. daily check-in,
5. habit history,
6. consistency measurement,
7. avatar energy and mood,
8. gradual avatar state changes,
9. basic achievements,
10. simple changes to the avatar's environment.

Do not add the following unless explicitly requested:

* social features
* friends
* leaderboards
* calorie counting
* macro tracking
* diet plans
* recipes
* AI coaching
* smartwatch integration
* complex backend infrastructure

## Development priorities

When choosing between multiple reasonable implementations, prefer:

* simple over unnecessarily complex,
* testable over tightly coupled,
* maintainable over clever,
* consistency over premature optimization,
* existing project patterns over introducing new patterns,
* small focused changes over broad refactoring.

Always read this `AGENTS.md` before making changes to the Jivana project.
