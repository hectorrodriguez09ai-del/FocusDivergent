# FocusDivergent · Technical documentation

Oct 11, 2026 · @Hector

## Overview

FocusDivergent is a native Android app, written in Kotlin and Jetpack Compose. It protects attention from the apps that take it, and it helps you start tasks. The current version is 1.0.0 beta, and everything works offline.

### Who it is for

"Neurodivergent" describes people whose brain works differently from the majority: ADHD, autism, dyslexia, dyspraxia, Tourette syndrome, high abilities (gifted) and other profiles. It is not a single diagnosis, and each person combines different traits.

The app is centred on ADHD and high abilities, but the difficulty it addresses is common to many profiles: executive function. Starting is hard, estimating time is hard, stopping an impulse is hard, and coming back after an interruption is hard. The design responds to that difficulty, and it does not require a diagnosis.

Each profile has its own needs, and the app does not cover all of them equally. An autistic person with sensory hypersensitivity benefits from a design with no flashing and reduced motion. Idea capture and micro-steps help with paralysis, but they do not replace specific support. The app does not replace a professional.

## Architecture

&#91;embedded content: Layers of FocusDivergent · arrows point toward the domain\]

The app has four layers. Each arrow is a dependency: the one that points depends on the other. The domain knows nothing about Android, so its rules are tested on the JVM without an emulator.

## Layers and packages

Each layer has one responsibility and one rule. The rules were checked against the current code.

| Package | Contents | Rule |
| --- | --- | --- |
| `domain` | Models, pure policies, use cases and repository interfaces. | No `android.*` or `androidx.*` imports. |
| `data` | Room (DAOs, entities, migrations), repositories, preferences and app usage. | Does not import `presentation`. |
| `presentation` | Compose screens, ViewModels, navigation and theme. | Does not import `data`: it asks for everything through the domain. |
| `service` | Accessibility service, blocking service, alarms, notifications, receivers and audio. | Calls use cases. Exception: the overlay imports `presentation` screens. |
| `di` | Hilt modules that bind each interface to its implementation. | Wiring only, no logic. |

The exception in `service` is in `CaptureTileService`, `FocusBlockerService` and `InterruptionOverlayController`. They draw Compose screens outside the activity, so they need the composables. A new change should not follow this path without a reason.

## Blocking engine

Blocking is a short chain. Android reports the app change, a service decides, and an overlay window shows the pause. No component reads the screen content.

1. `AppBlockerAccessibilityService` receives every window change and calls `BlockCheckTrigger.request()`. It decides nothing.
2. `FocusBlockerService` is a foreground service with `START_STICKY`. It merges those signals with a tick every 5 seconds, and it leaves at least 300 ms between checks.
3. Each check first applies the limit changes that have come due, closes the day if it is time, and then evaluates whether there is an interruption.
4. `EvaluateInterruptionUseCase` interrupts nothing while the screen is off, during a call, or in a dialer app. Otherwise it reads today's usage and looks in this order: Obligation reminder, Launchpad, app block, Reality Check.
5. `InterruptionOverlayController` shows or removes the window. Each interruption has a key, and while the key does not change the overlay is not redrawn.

| Interruption | When it appears | How it is resolved |
| --- | --- | --- |
| Obligation reminder | 10 minutes before its time (`ReminderPolicy.LEAD_MINUTES`). | Turning the screen off, reaching the time, or solving the challenge. |
| Launchpad | For 2 minutes, after you choose to start. | "Continue" keeps the task going; "I'll stop for now" leaves no trace. |
| App block | When the daily limit runs out, depending on the friction level. | Flexible: a challenge to earn 15 minutes, up to 3 times. Firm: closed until tomorrow. Closed: always paused. |
| Reality Check | After a long stretch in one app. | "Yes, on purpose" silences the prompt for a while; or you go home. |

## Data model

The database is Room, at version 8. It is the only source of truth, and nothing leaves the device. Each version exports its schema to `app/schemas/`.

| Table | Contents | Notes |
| --- | --- | --- |
| `tasks` | Tasks, Obligations and Routines. | Start time in epoch ms. Repetition as RRULE. |
| `task_steps` | Micro-steps of a task. | Deleted in cascade with their task. |
| `step_templates` | The user's step templates. | Steps are stored one per line. |
| `app_limits` | Daily limits per app. | Includes the friction level and the "leisure" flag. |
| `pending_limit_changes` | Loosened limits waiting for midnight. | Applied automatically when due. |
| `time_extensions` | 15-minute extensions earned with the challenge. | One row per extension. |
| `routine_completions` | Routines done, per day. | One row per routine and day. |
| `xp_events` | The XP and coins ledger. | One movement per cause, activity and day. The total is a sum. |
| `cosmetic_purchases` | Cosmetics bought. | Buying twice does not charge twice. |
| `user_stats` | A single row: the best streak. | Stores no XP or coin counters. |
| `brain_dumps` | Ideas in the inbox. |  |
| `vault_nodes` | Notes and folders in the Vault. | Hierarchy through `parent_id`. |
| `vault_nodes_fts` | Full-text search of the Vault. | FTS4 table, accent-insensitive. |

The version history lives in `FocusDatabase.kt`. These are its migrations:

| Version | Change |
| --- | --- |
| 1 | Initial schema. |
| 2 | `time_extensions` table. |
| 3 | `tasks.estimated_minutes`, `tasks.completed_at` and `routine_completions`. |
| 4 | `app_limits.extensions_per_day` and `pending_limit_changes`. |
| 5 | `vault_nodes.updated_at`. |
| 6 | XP ledger, cosmetics and `user_stats.best_streak`. Manual migration (`Migration5To6`) that records what was already done. |
| 7 | `app_limits.is_leisure`, `pending_limit_changes.is_leisure` and `xp_events.minutes`. |
| 8 | `task_steps` and `step_templates`. |

The Day Closure settings and the last closed day live in preferences. They need no relations.

## Rewards, streak and mascot

XP and coins are never stored as a counter. Each finished thing is a movement in `xp_events`, and the total is summed when it is shown. That way, ticking and unticking cannot inflate anything.

The values live in `RewardRules`, in a single file:

| Cause | XP | Coins |
| --- | --- | --- |
| Task or Obligation finished | 10 (15 if it needs high energy) | 1 |
| Routine done that day | 8 | 1 |
| Focus block of 10 minutes or more | 5 per 15-minute chunk | 1 per block |
| Continuing after the Launchpad | 5 | 0 |
| Task step done | 2 | 0 |
| Limit respected at the Day Closure | 5 per app | 0 |

There are daily caps of 100 XP and 10 coins. Unticking an activity removes its movement. Deleting an activity removes nothing.

**Streak.** `StreakCalculator` counts the days with at least one routine done. One empty day per week is forgiven silently. Today never breaks the streak, because the day is not over yet. If a streak breaks, there is no message, and the best streak is kept.

**Levels.** The curve is `30 + 10 × level` XP per level, with no ceiling. `Levels.of` computes the level from the total XP.

**Mascot.** There are three pixel-art companions: Fox, Cat and Alien. Each has five stages, which start at levels 1, 3, 6, 10 and 15 (`Mascot.LEVELS`). Cosmetics are bought with coins and placed next to the mascot.

## Screens and navigation

Navigation uses Navigation Compose with type-safe routes: each destination is a `@Serializable` class in `Routes.kt`. A misspelled destination or a missing argument is a compile error. There are four tabs (`TopLevelDestination`): Today, Tasks, Vault and Blocks. Switching tabs does not stack screens, and each tab keeps its state.

| Route | Screen | ViewModel |
| --- | --- | --- |
| `TodayRoute` | Today: energy, Pick for me, Launchpad, now and mascot | `TodayViewModel` |
| `ChooseForMeRoute` | Pick for me | `ChooseForMeViewModel` |
| `FocusLaunchRoute` | 2-minute Launchpad | `FocusLaunchViewModel` |
| `TasksRoute` | List of tasks, Obligations and routines | `TasksViewModel` |
| `TaskEditorRoute` | Create or edit an activity | `TaskEditorViewModel` |
| `StepsRoute` | Prepare the steps of a task | `StepsEditorViewModel` |
| `StepFocusRoute` | One step per screen | `StepFocusViewModel` |
| `TimerRoute` | Timer and Pomodoro | `TimerViewModel` |
| `AudioRoute` | Ambient sound | `AudioViewModel` |
| `MascotRoute` | My mascot and the shop | `MascotViewModel` |
| `VaultRoute` | Vault and idea inbox | `VaultViewModel` |
| `NoteEditorRoute` | Create or edit a note | `NoteEditorViewModel` |
| `BlocksRoute` | Blocks: limits and access | `BlocksViewModel` |
| `AppPickerRoute` | Pick the app for a limit | `AppPickerViewModel` |
| `LimitEditorRoute` | Create or change a limit | `LimitEditorViewModel` |
| `ClosureSettingsRoute` | Day Closure settings | `ClosureSettingsViewModel` |
| `ClosureRoute` | Summary of a closed day | `ClosureSummaryViewModel` |
| `AiSettingsRoute` | Steps with your own AI | `AiSettingsViewModel` |
| `PermissionsRoute` | Permissions and battery; the welcome screen on first run | `PermissionsViewModel` |

Quick idea capture has its own activity, `CaptureActivity`, with `CaptureViewModel`. It opens from the notification or the Quick Settings tile, on top of any app. The welcome screen is shown only once.

## Services, alarms and notifications

Each kind of reminder has its own receiver, its own alarm code and its own channel. Two alarms with the same code and the same `Intent` replace each other. That is why each routine's "Later" carries its own data in the `Intent`.

| Component | What it does | Code or channel |
| --- | --- | --- |
| `FocusBlockerService` | Watches the apps and shows interruptions. Foreground service. | Channel `focus_protection` |
| `AppBlockerAccessibilityService` | Reports every window change. | Accessibility permission |
| `ObligationAlarmReceiver` | Obligation reminder, 10 minutes before. | Alarm 1 |
| `TimerAlarmReceiver` | End of each timer phase. | Alarm 2 · channels `focus_timer` and `focus_timer_alerts` |
| `ClosureAlarmReceiver` | Day Closure time: closes the day and schedules the next. | Alarm 4 · channel `day_closure`, notification 30 |
| `RoutineAlarmReceiver` | General reminder for routines with a time. | Alarm 6 · channel `routine_reminders` |
| `RoutineSnoozeReceiver` | Repeats a postponed routine reminder once. | Alarm 7 |
| `RoutineActionReceiver` | The "Done" and "Later" buttons of the reminder. | Notification actions |
| `AmbientAudioService` | Plays ambient sound with the app closed. | Channel `ambient_audio` |
| `CaptureTileService` | The "Capture idea" tile in Quick Settings. | Tile service |
| `*RestartReceiver` (timer, closure, routines and protection) | Restores alarms after a reboot or an update, because Android clears them. | `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` |

On start, `FocusDivergentApp` reschedules the Day Closure, and the Obligation and routine synchronisers observe the database. Each time a task or routine changes, they recalculate the next alarm.

Alarms are exact when the user has granted the exact-alarm permission. Otherwise they are approximate, and the code accounts for it: the Day Closure also runs when the app is opened, and from the blocking service.

## Privacy and guarantees

The two core promises of the app, working offline and having no punitive visuals, do not depend on the goodwill of whoever writes the code. Tests fail if either one breaks.

| Guarantee | What enforces it | Test |
| --- | --- | --- |
| No network, except the optional AI | Only `ChatCompletionsSuggester` and `UrlConnectionPoster` open connections. Only `https`. Redirects are not followed. | `InternetGuardTest` |
| No extra network permission | Only `INTERNET`, with no `ACCESS_NETWORK_STATE` and no cleartext traffic. | `InternetGuardTest` |
| No red | Scans `src/main` for reddish colours and for `Color.Red` or `Color.Magenta`. | `Rnf3GuardTest` |
| No blaming or alarming text | Scans `strings.xml` for words such as "overdue" or "guilt", exclamation marks and all-caps words. | `Rnf3GuardTest` |
| High contrast | Text on each background reaches 7:1, in both palettes. | `ThemeContrastTest` |
| Policy and manifest agree | Every permission appears in `PRIVACY.md`. The accessibility service cannot read the screen. Backups are off. | `ReleaseReadinessTest` |

The AI key is encrypted with AES-GCM in the Android Keystore, and it is never stored in plain text in preferences (`KeystoreSecretCipher`). No backup contains it, because `allowBackup` is off.

If a change breaks a guarantee, the build fails. The fix is not to change the test: rethink the change or, if the decision is deliberate, document it in `PRIVACY.md` and in this section.

## Testing

There are 1,140 unit tests in 92 test files, all under `app/src/test`. There are no instrumented tests that need a device. Screen and database tests run on Robolectric, on the JVM.

| What is tested | How | Where |
| --- | --- | --- |
| Business rules | Pure functions and use cases, with in-memory doubles and a fixed clock (`TEST_CLOCK`). | `domain/` and `domain/usecase/Fakes*.kt` |
| Repositories and database | Room in memory. Migrations are checked with `MigrationTestHelper` and the exported schemas. | `data/` and `FocusDatabaseMigrationTest` |
| ViewModels | With `MainDispatcherRule` and observed state. | `presentation/*ViewModelTest` |
| Screens | Compose on Robolectric with `createComposeRule`. | `presentation/*ScreensTest` |
| Services and alarms | Robolectric with `ShadowAlarmManager` and `ShadowNotificationManager`. | `service/` |
| Project guarantees | Scan the code and resources, not the behaviour. | `Rnf3GuardTest`, `InternetGuardTest`, `ThemeContrastTest`, `ReleaseReadinessTest` |

**Control mutation.** Before accepting a change to a key rule, the rule is broken on purpose and a test is checked to fail. Then it is restored. For example: unticking a task stops removing its XP, and four tests detect it. This is how you know the tests protect the rule and not just cover it.

**Commands.** Results are written to `app/build/test-results/testDebugUnitTest/`. Lint runs with `:app:lintDebug`, and it must finish with no errors. The release commands are in the publishing section.

## Build, sign and publish

The project builds with Android Studio (JDK 17 or newer) and with the Gradle wrapper. From the root:

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:testDebugUnitTest        # unit tests
./gradlew :app:lintDebug                # static analysis
./gradlew :app:assembleRelease          # release APK, with R8
./gradlew :app:lintVitalRelease         # release lint
```

On Windows use `gradlew.bat`. The release APK is left unsigned if there is no `keystore.properties` file. That file is not in the repository, and `.gitignore` excludes it.

Signing and publishing follow their own process. Before each version:

1. Always bump `versionCode`, and `versionName` in `app/build.gradle.kts`.
2. Add the release notes in `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`. The store listing currently exists only in Spanish (`es-ES`).
3. Review `PRIVACY.md` if a permission or a data item changes.
4. Test the release APK on a real phone. R8 can remove classes that are only used through reflection.

The project's own R8 rules are in `app/proguard-rules.pro`. If a new dependency uses reflection, its rules go there.

## Contributing

Contributions are welcome, but the project has two principles that are not negotiable. A change that breaks either one is not accepted, even if it works well.

1. **No red and no blame (RNF-3).** No reddish colour, and no text that blames, shouts or alarms. `Rnf3GuardTest` checks this in the code and in the strings.
2. **Everything local (RNF-1).** No connection, no analytics, and no dependency that calls a server. The only exception is the optional AI, and `InternetGuardTest` prevents that exception from growing.

In addition:

- **Layers.** The domain does not import Android, `presentation` does not import `data`, and data changes go through the domain. Details are in the section "Layers and packages".
- **Strings.** Every visible text goes in `res/values/strings.xml`, never written in code. Comments and documentation are in Spanish, and identifiers are in English.
- **Database.** A schema change bumps `version`, adds a migration and exports the new JSON to `app/schemas`. The migration is tested with `MigrationTestHelper`.
- **Tests.** A rule change comes with its test. If it touches a key rule, run a control mutation before accepting the change.
- **Checks.** Before opening a change: `assembleDebug`, `testDebugUnitTest` and `lintDebug`, with no errors.

A design change is proposed before it is coded. Describe the problem, the rule that changes, and how it will be tested.

## Design decisions

Every decision has a reason. If you change one, first review the reason.

| Decision | Reason | Where it shows in the code |
| --- | --- | --- |
| XP is a ledger of movements, not a counter. | A counter inflates when you tick and untick. With a ledger, unticking removes the movement. | `RewardLedger` |
| The streak has one grace day per week and no loss message. | RNF-3: breaking a streak must not punish. One routine a day is enough. | `StreakCalculator` |
| Loosening a limit waits until midnight. Tightening it is immediate. | Decide with a cool head, not with the impulse in front of you. | `LimitChangePolicy` |
| Removing a limit is immediate, but requires a maths challenge. | Stops people deleting the limit and recreating it with more room. | `AppLimitUseCases` |
| The Day Closure derives the free night from the time, not from the alarm. | An alarm can be late or never fire. The night must not depend on it. | `ClosureWindow` |
| The palette has no red. Black, electric blue and purple. | RNF-3. Red triggers an alarm response without need. | `Color.kt`, `Rnf3GuardTest` |
| Unfinished tasks are never marked as overdue. | RNF-3. Today's list has no overdue section. | `TaskAgenda` |
| Ambient sound is generated on the phone, with no downloaded files. | RNF-1. No network, no audio copies, no content dependencies. | `domain/audio/` |
| AI is optional, uses the user's key and sends only the title. | RNF-1. The only connection is the one the user switches on. | `ChatCompletionsSuggester` |
| The accessibility service cannot read the screen content. | Privacy: the app only needs to know which app is in front. | `accessibility_service_config.xml` |
| The mascots are pixel art cut from a sprite sheet by the author. | An original drawing, with no third-party images. | `res/drawable-nodpi/mascot_*.png` |

Each decision has its reason in the code and in its tests. If you change one, review the reason and the test that protects it first.

## Where to find things

| Question | File |
| --- | --- |
| Where is the rule that decides whether an app is blocked? | `domain/usecase/EvaluateInterruptionUseCase.kt` |
| How much is each action worth in XP and coins? | `domain/reward/RewardRules.kt` |
| How is the streak calculated? | `domain/reward/Streaks.kt` |
| Which tables does the database have? | `data/local/entity/` and `data/local/FocusDatabase.kt` |
| How does one version migrate to the next? | `data/local/FocusDatabase.kt` and `data/local/Migrations.kt` |
| Which texts does the user see? | `res/values/strings.xml` |
| Where is each interface connected to its implementation? | `di/` |
| What happens when the app starts? | `FocusDivergentApp.kt` and `MainActivity.kt` |
| Which permissions does the app ask for? | `AndroidManifest.xml` and `PRIVACY.md` |
| What does the project guarantee, and what checks it? | `app/src/test/java/com/focusdivergent/app/` (the `*GuardTest` tests) |
