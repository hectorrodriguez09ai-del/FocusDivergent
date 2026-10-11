package com.focusdivergent.app.di

import com.focusdivergent.app.data.device.AndroidDeviceState
import com.focusdivergent.app.data.device.AndroidInstalledAppsRepository
import com.focusdivergent.app.data.settings.SharedPreferencesAudioSettings
import com.focusdivergent.app.data.settings.SharedPreferencesCaptureTileOffer
import com.focusdivergent.app.data.settings.SharedPreferencesDailyEnergyStore
import com.focusdivergent.app.data.settings.SharedPreferencesDayClosureSettings
import com.focusdivergent.app.data.settings.SharedPreferencesDayClosureStore
import com.focusdivergent.app.data.settings.SharedPreferencesFocusLaunchStore
import com.focusdivergent.app.data.settings.SharedPreferencesMascotSettings
import com.focusdivergent.app.data.settings.SharedPreferencesOnboardingState
import com.focusdivergent.app.data.settings.SharedPreferencesRealityCheckSettings
import com.focusdivergent.app.data.settings.SharedPreferencesTimerSettings
import com.focusdivergent.app.data.settings.SharedPreferencesTimerStore
import com.focusdivergent.app.domain.alarm.ObligationAlarmScheduler
import com.focusdivergent.app.domain.closure.ClosureAlarmScheduler
import com.focusdivergent.app.domain.closure.ClosureNotifier
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DayClosureStore
import com.focusdivergent.app.domain.audio.AmbientAudio
import com.focusdivergent.app.domain.audio.AudioSettings
import com.focusdivergent.app.domain.device.DeviceState
import com.focusdivergent.app.domain.launch.FocusLaunchStore
import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import com.focusdivergent.app.domain.reward.MascotSettings
import com.focusdivergent.app.domain.settings.CaptureTileOffer
import com.focusdivergent.app.domain.settings.DailyEnergyStore
import com.focusdivergent.app.domain.settings.OnboardingState
import com.focusdivergent.app.domain.timer.TimerAlarmScheduler
import com.focusdivergent.app.domain.timer.TimerNotifier
import com.focusdivergent.app.domain.timer.TimerSettings
import com.focusdivergent.app.domain.timer.TimerStore
import com.focusdivergent.app.service.AndroidAmbientAudio
import com.focusdivergent.app.domain.reminder.RoutineReminderAlarms
import com.focusdivergent.app.domain.reminder.RoutineReminderLog
import com.focusdivergent.app.domain.reminder.RoutineReminderNotifier
import com.focusdivergent.app.service.AndroidRoutineReminderAlarms
import com.focusdivergent.app.service.AndroidRoutineReminderNotifier
import com.focusdivergent.app.service.SharedPreferencesRoutineReminderLog
import com.focusdivergent.app.service.AndroidClosureAlarmScheduler
import com.focusdivergent.app.service.AndroidClosureNotifier
import com.focusdivergent.app.service.AndroidObligationAlarmScheduler
import com.focusdivergent.app.service.AndroidTimerAlarmScheduler
import com.focusdivergent.app.service.AndroidTimerNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {

    @Binds
    abstract fun bindDeviceState(impl: AndroidDeviceState): DeviceState

    @Binds
    abstract fun bindInstalledAppsRepository(impl: AndroidInstalledAppsRepository): InstalledAppsRepository

    @Binds
    abstract fun bindRealityCheckSettings(impl: SharedPreferencesRealityCheckSettings): RealityCheckSettings

    @Binds
    abstract fun bindFocusLaunchStore(impl: SharedPreferencesFocusLaunchStore): FocusLaunchStore

    @Binds
    abstract fun bindDailyEnergyStore(impl: SharedPreferencesDailyEnergyStore): DailyEnergyStore

    @Binds
    abstract fun bindCaptureTileOffer(impl: SharedPreferencesCaptureTileOffer): CaptureTileOffer

    @Binds
    abstract fun bindOnboardingState(impl: SharedPreferencesOnboardingState): OnboardingState

    @Binds
    abstract fun bindObligationAlarmScheduler(impl: AndroidObligationAlarmScheduler): ObligationAlarmScheduler

    @Binds
    abstract fun bindAudioSettings(impl: SharedPreferencesAudioSettings): AudioSettings

    @Binds
    abstract fun bindMascotSettings(impl: SharedPreferencesMascotSettings): MascotSettings

    @Binds
    abstract fun bindDayClosureSettings(impl: SharedPreferencesDayClosureSettings): DayClosureSettings

    @Binds
    abstract fun bindDayClosureStore(impl: SharedPreferencesDayClosureStore): DayClosureStore

    @Binds
    abstract fun bindClosureAlarmScheduler(impl: AndroidClosureAlarmScheduler): ClosureAlarmScheduler

    @Binds
    abstract fun bindClosureNotifier(impl: AndroidClosureNotifier): ClosureNotifier

    @Binds
    abstract fun bindRoutineReminderAlarms(impl: AndroidRoutineReminderAlarms): RoutineReminderAlarms

    @Binds
    abstract fun bindRoutineReminderNotifier(impl: AndroidRoutineReminderNotifier): RoutineReminderNotifier

    @Binds
    abstract fun bindRoutineReminderLog(impl: SharedPreferencesRoutineReminderLog): RoutineReminderLog

    @Binds
    abstract fun bindAmbientAudio(impl: AndroidAmbientAudio): AmbientAudio

    @Binds
    abstract fun bindTimerStore(impl: SharedPreferencesTimerStore): TimerStore

    @Binds
    abstract fun bindTimerSettings(impl: SharedPreferencesTimerSettings): TimerSettings

    @Binds
    abstract fun bindTimerAlarmScheduler(impl: AndroidTimerAlarmScheduler): TimerAlarmScheduler

    @Binds
    abstract fun bindTimerNotifier(impl: AndroidTimerNotifier): TimerNotifier
}
