# Create Habit and weekly scheduling

The production entry point is **Home → Create habit** (PL: **Dodaj nawyk**).
The form accepts a name, optional multiline description and either all seven days
or selected weekdays. Saving returns to the previous screen. This change does not
add a habit list, completion controls, editing, archiving, reminders or rewards.

## Model and persistence

`Habit` contains a stable UUID independent of its name, normalized name, optional
description, `Set<DayOfWeek>`, local `startDate` and optional `Instant archivedAt`.
Creation trims the name, collapses whitespace in the name, trims the description
while preserving its internal line breaks and converts a blank description to null.
An empty name or empty weekday selection is rejected in the domain use case.

`HabitEntity` persists weekdays as a seven-bit integer and dates as epoch days.
`scheduledThroughEpochDay` is a persistence cursor, separate from the domain habit.
There is no numerical goal: existing history still allows one completion per habit
and local calendar date.

Migration 1 → 2 only adds the `habits` table. The three original tables and their
foreign keys are unchanged. It neither deletes legacy records nor invents names,
rules or definitions. A legacy ID may therefore still have history and no matching
definition (`getById` returns null). Such history continues to participate in Jive
scoring; only real definitions participate in automatic weekly generation. A future
history/list feature must retain this distinction rather than assume an inner join.

`HabitRepository` owns definitions and generation cursors. Its Room implementation
coordinates transactions and delegates planned-day writes to the existing
`HabitHistoryRepository.scheduleHabit`. Completion writes and reaction publication
remain exclusively in the existing history implementation.

## Atomic creation and retries

`CreateHabitUseCase` generates the stable habit ID from a random request token.
The UI keeps the token and primitive form fields in `SavedStateHandle`, scoped to
the navigation entry. The same request can be retried without creating a second
definition, including after an Activity is restored. New forms get new tokens, so
two intentionally created habits may share a name.

Definition, today's initial occurrence (when selected), and the initial cursor are
saved in one Room transaction. If today is not selected, no future occurrence is
inserted; the definition still starts today. Any failure rolls back the definition
and its initial schedule together. The UI disables save, editing and back while a
save is pending, shows progress and retains its input after an I/O/database error.

## Incremental scheduling and scoring order

`EnsureHabitSchedulesUseCase` reads only definitions with unfinished progress.
It generates matching weekdays after the committed cursor through the requested
local date, inclusive, without generating dates before `startDate` or in the future.
Transactions cover at most 366 calendar days per habit, allowing large absences to
be processed in bounded batches. Non-selected days also advance the cursor.

Each batch checks that its cursor is still current, then commits schedules and the
new cursor in the same transaction. Competing generators cannot advance stale
progress. Failed batches leave both the previous cursor and planned days intact.
Previously completed occurrences are preserved through the existing insert-ignore
schedule operation. Planning never records a daily interaction or emits a reaction.

`ObserveJiveConditionUseCase` completes scheduling before subscribing to history.
It also checks progress before scoring emissions: if a late creation introduces
overdue work (for example around midnight), the snapshot is reread after catch-up.
There remains only one scoring implementation, `JiveConditionScorer`.

The lifecycle-bound date monitor refreshes on initialization and every resume. While
RESUMED it wakes at local midnight and checks clock/zone changes at most every
60 seconds. It stops in the background and catches up on resume, without a worker,
alarm, notification or new scheduling library. The injected clock resolves the
current device zone on each read. A backwards local-date change never rewinds the
cursor or deletes occurrences already generated; the history query still respects
its requested through-date.

## Changed files

New production files:

- `app/src/main/java/com/uniccomj/jivana/domain/model/Habit.kt`
- `app/src/main/java/com/uniccomj/jivana/domain/repository/HabitRepository.kt`
- `app/src/main/java/com/uniccomj/jivana/domain/usecase/CreateHabitUseCase.kt`
- `app/src/main/java/com/uniccomj/jivana/domain/usecase/EnsureHabitSchedulesUseCase.kt`
- `app/src/main/java/com/uniccomj/jivana/data/local/entity/HabitEntity.kt`
- `app/src/main/java/com/uniccomj/jivana/data/local/dao/HabitDao.kt`
- `app/src/main/java/com/uniccomj/jivana/data/local/database/JivanaMigrations.kt`
- `app/src/main/java/com/uniccomj/jivana/data/mapper/HabitMapper.kt`
- `app/src/main/java/com/uniccomj/jivana/data/repository/RoomHabitRepository.kt`
- `app/src/main/java/com/uniccomj/jivana/di/HabitModule.kt`
- `app/src/main/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitUiState.kt`
- `app/src/main/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitViewModel.kt`
- `app/src/main/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitScreen.kt`
- `app/schemas/com.uniccomj.jivana.data.local.database.JivanaDatabase/2.json`

Modified production/configuration files:

- `app/build.gradle.kts` — packages exported schemas as Android test assets.
- `app/src/main/java/com/uniccomj/jivana/MainActivity.kt` — lifecycle monitor, retry UI and Hilt factory.
- `app/src/main/java/com/uniccomj/jivana/data/local/database/JivanaDatabase.kt` — schema version 2.
- `app/src/main/java/com/uniccomj/jivana/di/DatabaseModule.kt` — registers the migration.
- `app/src/main/java/com/uniccomj/jivana/di/RepositoryModule.kt` — binds the new repository.
- `app/src/main/java/com/uniccomj/jivana/di/JiveModule.kt` — scheduling dependency and current-zone clock.
- `app/src/main/java/com/uniccomj/jivana/domain/usecase/ObserveJiveConditionUseCase.kt` — scheduling before scoring.
- `app/src/main/java/com/uniccomj/jivana/presentation/avatar/JiveConditionViewModel.kt` — day refresh and errors.
- `app/src/main/java/com/uniccomj/jivana/presentation/home/HomeScreen.kt` — form entry button.
- `app/src/main/java/com/uniccomj/jivana/presentation/navigation/AppDestination.kt` — create route.
- `app/src/main/java/com/uniccomj/jivana/presentation/navigation/AppNavHost.kt` — entry-scoped form ViewModel.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-pl/strings.xml` — EN/PL form strings.

New tests:

- `app/src/test/java/com/uniccomj/jivana/domain/usecase/HabitUseCasesTest.kt`
- `app/src/test/java/com/uniccomj/jivana/domain/usecase/HabitScoringOrderTest.kt`
- `app/src/test/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitViewModelTest.kt`
- `app/src/androidTest/java/com/uniccomj/jivana/data/repository/RoomHabitRepositoryTest.kt`
- `app/src/androidTest/java/com/uniccomj/jivana/data/repository/HabitMigrationTest.kt`
- `app/src/androidTest/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitScreenTest.kt`
- `app/src/androidTest/java/com/uniccomj/jivana/presentation/habitcreate/CreateHabitNavigationTest.kt`

Updated tests:

- `app/src/test/java/com/uniccomj/jivana/presentation/avatar/JiveConditionViewModelTest.kt`

## Verification

Verified on 2026-11-05 on the Pixel 7 emulator (Android 17 / API 37):

| Check | Result |
| --- | --- |
| DEV Debug unit tests | 322 passed, 0 failed |
| PROD Debug unit tests | 322 passed, 0 failed |
| DEV Android tests, including migration | 46 passed, 0 failed |
| PROD Android tests, including migration | 42 passed, 0 failed |
| `qualityCheck` | Passed (ktlint, detekt and Android lint; lint has warnings, no errors) |
| DEV Debug / PROD Debug APKs | Built successfully |
| DEV Release / PROD Release APKs | Built successfully; unsigned release artifacts |
| Production Activity started with an actual v1 database | Passed: migrated to v2 and Activity RESUMED |

The separate device upgrade check copied an original-schema v1 fixture into the
DEV app's database location while the app was stopped, then launched the built
production Activity. It verified schema version 2, the exact legacy habit ID in
the plan and completion tables, its daily interaction, and zero invented habit
definitions. The previous emulator database was backed up and restored, with
SHA-256 verification. Generated evidence is in the ignored build directory:
`app/build/outputs/create-habit-verification/upgrade-result.json` and
`app/build/outputs/create-habit-verification/upgraded-home.png`.

Unit tests cover normalization, required fields, request identity, daily/weekly
rules, month/year/leap boundaries, absence, incremental batches, repeated execution,
backwards date/zone changes, midnight monitoring, scoring order, save failure and
SavedStateHandle restoration. Android tests cover real transactions, rollback after
injected failures, competing generators/retries, preservation of completions and
absence of planning reactions, form validation/errors, disabled duplicate save,
navigation, actual Activity recreation and persisted Room values.

The migration test builds the v1 fixture from the committed `1.json` export and
opens it through the production Room builder, which executes the migration and
validates the generated v2 schema. It checks legacy history and a new creation.
It uses Android's JSON reader because `MigrationTestHelper`'s schema deserializer
encountered an `AbstractMethodError` with the existing transitive serialization
runtime on this emulator; no dependency versions were changed to work around it.

Validation commands:

```powershell
.\gradlew.bat qualityCheck :app:testDevDebugUnitTest :app:testProdDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDevDebug :app:assembleProdDebug :app:assembleDevRelease :app:assembleProdRelease --console=plain
.\gradlew.bat :app:connectedDevDebugAndroidTest --console=plain
.\gradlew.bat :app:connectedProdDebugAndroidTest --console=plain
```

## Manual checks and current limits

1. Open Home and choose Create habit / Dodaj nawyk.
2. Save an empty name, then a whitespace-only name: validation must appear.
3. Enter a name and multiline description; choose selected days and clear every
   day: saving must show weekday validation. Select Monday, Wednesday and Friday.
4. Rotate/recreate the Activity: the text, frequency and weekdays must remain.
5. Save twice quickly: saving must disable further submission and return Home
   once. Reopening Create must show a fresh form.
6. Create an every-day habit and restart the application on a later local date.
   Inspect Room to confirm every due date, no future records and no interactions.
7. Keep the app open across midnight; repeat after changing the device time zone.
   Scheduling and the existing condition observer must refresh automatically.
8. For an upgrade, use a v1 database with schedules/completions and no definitions,
   install/open the new build, and verify v2 plus unchanged legacy records. Do not
   clear app data or uninstall as part of an upgrade check.

There is intentionally no list/detail screen to inspect created habits in the app
yet; use Android Studio Database Inspector until those features are implemented.
Time/zone changes while foregrounded are detected within 60 seconds. Background
days are materialized on the next resume, before scoring, rather than by a service.
No new libraries or changes to scoring configuration, visual state resolution,
animation, character drawing, wardrobe or `HabitCompletionEvents` are included.
