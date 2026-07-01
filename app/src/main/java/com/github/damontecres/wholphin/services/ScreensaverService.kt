package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.shouldFetchRtAudience
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.components.ScreensaverItem
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles the queue of items to show on the screensaver, both in-app or OS
 */
@Singleton
class ScreensaverService
    @Inject
    constructor(
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val userPreferencesService: UserPreferencesService,
        private val homeSettingsService: HomeSettingsService,
        private val mdbListRatingsService: MdbListRatingsService,
    ) {
        private val _state = MutableStateFlow(ScreensaverState(false, false, false, false))
        val state: StateFlow<ScreensaverState> = _state

        val keepScreenOn = MutableStateFlow(false)

        private var waitJob: Job? = null

        init {
            userPreferencesService.flow
                .onEach { prefs ->
                    _state.update {
                        val enabled =
                            prefs.appPreferences.interfacePreferences.screensaverPreference.enabled
                        keepScreenOnInternal(enabled)
                        ScreensaverState(enabled, false, false, false)
                    }
                }.launchIn(scope)
        }

        /**
         * Reset the timer before showing the in-app screensaver
         */
        fun pulse() {
            waitJob?.cancel()
            if (_state.value.enabled) {
//                Timber.v("pulse")
                _state.update {
                    if (!it.active) {
                        it.copy(active = false)
                    } else {
                        it
                    }
                }

                if (!_state.value.paused) {
                    waitJob =
                        scope.launch(ExceptionHandler()) {
                            val startDelay =
                                userPreferencesService
                                    .getCurrent()
                                    .appPreferences.interfacePreferences.screensaverPreference.startDelay.milliseconds
                            delay(startDelay)
                            _state.update {
                                it.copy(active = true)
                            }
                        }
                }
            }
        }

        /**
         * Immediately start the in-app screensaver
         */
        fun start() {
            _state.update {
                it.copy(
                    enabledTemp = true,
                    active = true,
                )
            }
        }

        /**
         * Immediately stop the in-app screensaver
         */
        fun stop(cancelJob: Boolean) {
            _state.update {
                it.copy(
                    enabledTemp = false,
                    active = false,
                )
            }
            if (cancelJob) waitJob?.cancel()
        }

        /**
         * Signal to the OS for keeping the screen on such as during playback or when the in-app screensaver is active
         */
        fun keepScreenOn(keep: Boolean) {
            scope.launchDefault {
                val screensaverEnabled = _state.value.enabled
                Timber.d("Keep screen on: %s, screensaverEnabled=%s", keep, screensaverEnabled)
                if (screensaverEnabled) {
                    // Page is requesting to keep screen on, so we don't wait to show the screensaver
                    _state.update {
                        it.copy(active = false, paused = keep)
                    }
                    if (!keep) {
                        pulse()
                    }
                } else {
                    keepScreenOnInternal(keep)
                }
            }
        }

        private fun keepScreenOnInternal(keep: Boolean) {
            keepScreenOn.update { keep }
        }

        /**
         * Create a flow of items to show on the screensaver
         */
        fun createItemFlow(_scope: CoroutineScope): Flow<ScreensaverItem?> =
            flow {
                val items =
                    try {
                        homeSettingsService.fetchCurrentMediaBannerItems()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error loading media banner items for screensaver")
                        emit(ScreensaverItem.Error(ex))
                        return@flow
                    }
                if (items.isEmpty()) {
                    emit(ScreensaverItem.Empty)
                } else {
                    Timber.v("Got %s media banner items for screensaver", items.size)
                    emit(
                        ScreensaverItem.CurrentItems(
                            items = items,
                            audienceScores = emptyMap(),
                        ),
                    )
                    val audienceScores = loadMediaBannerAudienceScores(items)
                    if (audienceScores.isNotEmpty()) {
                        emit(
                            ScreensaverItem.CurrentItems(
                                items = items,
                                audienceScores = audienceScores,
                            ),
                        )
                    }
                }
            }.flowOn(Dispatchers.IO).cancellable()

        private suspend fun loadMediaBannerAudienceScores(items: List<BaseItem>): Map<UUID, Float> {
            val interfacePreferences =
                userPreferencesService.getCurrent().appPreferences.interfacePreferences
            if (!interfacePreferences.shouldFetchRtAudience()) {
                return emptyMap()
            }
            return items
                .mapNotNull { item ->
                    mdbListRatingsService
                        .getRottenTomatoesAudienceScore(item)
                        ?.takeIf { it > 0f }
                        ?.let { item.id to it }
                }.toMap()
        }
    }

data class ScreensaverState(
    val enabled: Boolean,
    val enabledTemp: Boolean,
    val active: Boolean,
    val paused: Boolean,
) {
    val show get() = (enabled || enabledTemp) && active && !paused
}
