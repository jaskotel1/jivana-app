# Habit completion reactions

## Existing write and observation paths

Habit completion is written through `HabitHistoryRepository.setHabitCompleted` →
`RoomHabitHistoryRepository` → the Room transaction in `HabitHistoryDao`.
There is currently no completion UseCase, habit ViewModel, or working habit screen;
the Habits navigation destination is a placeholder. The production write method
previously had no UI callers. The integration is ready for that future screen to
use the same repository path, without introducing another completion operation.

Room history flows through `ObserveJiveConditionUseCase` and `JiveConditionScorer`
to `JiveConditionViewModel`, then `JiveConditionController.condition`.
`MainActivity` passes the condition through navigation to Home.
`JiveVisualStateResolver` maps Mood, Energy and Sleepiness into visual targets and
continues to ignore the legacy `JiveCondition.reaction` field.

## Event path

The DAO transaction now reports whether a completion was newly inserted. Room's
`INSERT IGNORE` result distinguishes a new completion from an idempotent repeat
atomically, including concurrent writes. After the transaction returns successfully,
the existing repository publishes a notification on the shared, in-memory
`HabitCompletionEvents` instance. Failed or rolled-back writes, duplicate writes,
undo, scheduling, check-ins and history reads do not emit completion events.
Undo followed by completion is a new completion and celebrates again.

`HabitCompletionEvents` uses `SharedFlow<Unit>` with `replay = 0`, one extra buffer
slot and `DROP_OLDEST`. Notifications do not suspend database writes, survive a
process restart, or accumulate while there is no collector. A slow collector can
coalesce a burst to the most recent notification.

`JiveConditionController.reactions` maps live completions to the existing
`JiveReaction.CELEBRATING`. `JiveConditionViewModel` exposes that separate flow;
Activity and navigation pass it through Home to `JiveHomeCharacter`. No reaction
is written to `StateFlow<JiveCondition>`, Room, or saved UI state.

The character collects only while its navigation entry is RESUMED. Notifications
received while Home is absent/inactive are discarded, with no replay on navigation,
recreation or resume. An already running animation pauses with the existing clock
and continues when the same composition resumes.

## Playback

`JiveAnimator.react(CELEBRATING)` starts the existing HAPPY jump (1.1 seconds).
A second completion restarts from the displayed pose, without a playback queue.
The jump envelope temporarily blends toward the positive, awake celebration pose,
so severe sadness, exhaustion or sleep cannot hide the reaction. Its influence
fades back to zero at the end. Condition targets keep updating throughout playback;
the existing automatic transition to IDLE reveals the latest condition.

`JiveAnimationClock` remains the only playback clock. No wall-clock delays, extra
frame loops or changes to condition scoring are needed. When system animations
are disabled, new reactions are ignored and a running reaction is cleared to its
resting condition; enabling animations does not replay it.

## Verification

Unit tests cover successful versus failed/duplicate writes, repeated events,
condition independence, no replay, positive playback in extreme conditions,
condition updates during playback, smooth restart and disabled animations.
Room instrumentation verifies actual insert/undo/duplicate behavior. Compose
instrumentation connects Room events to the character and verifies visible
celebration, return to condition, inactivity, resume and absence of replay.
