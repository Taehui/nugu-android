/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.capabilityagents.asr.ExpectSpeechPayload
import com.skt.nugu.sdk.core.capabilityagents.audioplayer.AudioItem
import com.skt.nugu.sdk.core.capabilityagents.audioplayer.ProgressTimer
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.mediaplayer.*
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackButton
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.net.URI
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashSet

object DefaultAudioPlayerAgent {
    private const val TAG = "AudioPlayerAgent"

    val FACTORY = object : AudioPlayerAgentFactory {
        override fun create(
            mediaPlayer: MediaPlayerInterface,
            messageSender: MessageSender,
            focusManager: FocusManagerInterface,
            contextManager: ContextManagerInterface,
            playbackRouter: PlaybackRouter,
            playSynchronizer: PlaySynchronizerInterface,
            channelName: String,
            displayAgent: DisplayAgentInterface?
        ): AbstractAudioPlayerAgent =
            Impl(
                mediaPlayer,
                messageSender,
                focusManager,
                contextManager,
                playbackRouter,
                playSynchronizer,
                channelName
            ).apply {
                setDisplayAgent(displayAgent)
            }
    }

    internal data class PlayPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("audioItem")
        val audioItem: AudioItem
    ) {
        companion object {
            fun create(jsonString: String): ExpectSpeechPayload? = Gson().fromJson(jsonString, ExpectSpeechPayload::class.java)
        }
    }

    internal class Impl(
        mediaPlayer: MediaPlayerInterface,
        messageSender: MessageSender,
        focusManager: FocusManagerInterface,
        contextManager: ContextManagerInterface,
        playbackRouter: PlaybackRouter,
        playSynchronizer: PlaySynchronizerInterface,
        channelName: String
    ) : AbstractAudioPlayerAgent(
        mediaPlayer,
        messageSender,
        focusManager,
        contextManager,
        playbackRouter,
        playSynchronizer,
        channelName
    ), MediaPlayerControlInterface.PlaybackEventListener {
        companion object {
            const val EVENT_NAME_PLAYBACK_STARTED = "PlaybackStarted"
            const val EVENT_NAME_PLAYBACK_FINISHED = "PlaybackFinished"
            const val EVENT_NAME_PLAYBACK_STOPPED = "PlaybackStopped"
            const val EVENT_NAME_PLAYBACK_PAUSED = "PlaybackPaused"
            const val EVENT_NAME_PLAYBACK_RESUMED = "PlaybackResumed"
            const val EVENT_NAME_PLAYBACK_FAILED = "PlaybackFailed"

            const val EVENT_NAME_PROGRESS_REPORT_DELAY_ELAPSED = "ProgressReportDelayElapsed"
            const val EVENT_NAME_PROGRESS_REPORT_INTERVAL_ELAPSED = "ProgressReportIntervalElapsed"

            private const val NAME_NEXT_COMMAND_ISSUED = "NextCommandIssued"
            private const val NAME_PREVIOUS_COMMAND_ISSUED = "PreviousCommandIssued"
            private const val NAME_PLAY_COMMAND_ISSUED = "PlayCommandIssued"
            private const val NAME_PAUSE_COMMAND_ISSUED = "PauseCommandIssued"
            private const val NAME_STOP_COMMAND_ISSUED = "StopCommandIssued"

            private const val KEY_PLAY_SERVICE_ID = "playServiceId"
            private const val KEY_TOKEN = "token"
        }

        enum class PauseReason {
            BY_PAUSE_DIRECTIVE,
            BY_PLAY_DIRECTIVE_FOR_RESUME,
            BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY,
            INTERNAL_LOGIC,
        }

        private val activityListeners =
            HashSet<AudioPlayerAgentInterface.Listener>()
        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)

        private val executor = Executors.newSingleThreadExecutor()

        private var currentActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.IDLE
        private var focus: FocusState = FocusState.NONE

        private var currentItem: AudioInfo? = null
        private var nextItem: AudioInfo? = null
        private var token: String = ""
        private var sourceId: SourceId = SourceId.ERROR()
        private var offset: Long = 0L
        private var duration: Long = MEDIA_PLAYER_INVALID_OFFSET
        private var playNextItemAfterStopped: Boolean = false
        private var playCalled = false
        private var stopCalled = false
        private var pauseCalled = false
        private var pauseReason: PauseReason? = null
        private var progressTimer = ProgressTimer()
        private val progressProvider = object :
            ProgressTimer.ProgressProvider {
            override fun getProgress(): Long = getOffsetInMilliseconds()
        }
        private val progressListener = object :
            ProgressTimer.ProgressListener {
            override fun onProgressReportDelay(request: Long, actual: Long) {
                Logger.d(TAG, "[onProgressReportDelay] request: $request / actual: $actual")
                sendProgressReportDelay(actual)
            }

            override fun onProgressReportInterval(request: Long, actual: Long) {
                Logger.d(TAG, "[onProgressReportInterval] request: $request / actual: $actual")
                sendProgressReportInterval(actual)
            }
        }


        private inner class AudioInfo(
            val audioItem: AudioItem,
            val directive: Directive,
            val playServiceId: String
        ) : PlaySynchronizerInterface.SynchronizeObject {
            val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
                override fun onGranted() {
                    if (focus != FocusState.NONE) {
                        if (currentItem == this@AudioInfo) {
                            focusManager.releaseChannel(channelName, this@Impl)
                        }
                    }
                }

                override fun onDenied() {
                }
            }

            override fun getDialogRequestId(): String = directive.getDialogRequestId()

            override fun requestReleaseSync(immediate: Boolean) {
                executor.submit {
                    executeCancelAudioInfo(this)
                }
            }
        }

        init {
            mediaPlayer.setPlaybackEventListener(this)
            contextManager.setStateProvider(namespaceAndName, this)
        }

        private fun executeCancelAudioInfo(audioInfo: AudioInfo) {
            if (nextItem == audioInfo) {
                executeCancelNextItem()
            } else if (currentItem == audioInfo) {
                executeStop()
            } else {
                playSynchronizer.releaseSyncImmediately(audioInfo, audioInfo.onReleaseCallback)
            }
        }

        override fun preHandleDirective(info: DirectiveInfo) {
            // no-op
            when (info.directive.getNamespaceAndName()) {
                PLAY -> preHandlePlayDirective(info)
            }
        }

        private fun preHandlePlayDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[preHandlePlayDirective] info : $info")
            val playPayload = MessageFactory.create(info.directive.payload, PlayPayload::class.java)
            if(playPayload == null) {
                Logger.w(TAG, "[preHandlePlayDirective] invalid payload")
                setHandlingFailed(info, "[preHandlePlayDirective] invalid payload")
                return
            }

            val playServiceId = playPayload.playServiceId
            if (playServiceId.isNullOrBlank()) {
                Logger.w(TAG, "[preHandlePlayDirective] playServiceId is empty")
                setHandlingFailed(info, "[preHandlePlayDirective] playServiceId is empty")
                return
            }

            val audioInfo = AudioInfo(playPayload.audioItem, info.directive, playServiceId).apply {
                playSynchronizer.prepareSync(this)
            }

            executor.submit {
                executeCancelNextItem()
                nextItem = audioInfo
            }
        }

        override fun handleDirective(info: DirectiveInfo) {
            when (info.directive.getNamespaceAndName()) {
                PLAY -> handlePlayDirective(info)
                STOP -> handleStopDirective(info)
                PAUSE -> handlePauseDirective(info)
                else -> handleUnknownDirective(info)
            }
        }

        private fun handlePlayDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[handlePlayDirective] info : $info")

            setHandlingCompleted(info)
            executor.submit {
                executeHandlePlayDirective(info)
            }
        }

        private fun handleStopDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[handleStopDirective] info : $info")
            setHandlingCompleted(info)
            executor.submit {
                executeCancelNextItem()
                executeStop()
            }
        }

        private fun executeCancelNextItem() {
            val item = nextItem
            nextItem = null

            if (item == null) {
                Logger.d(TAG, "[executeCancelNextItem] no next item.")
                return
            }
            Logger.d(TAG, "[executeCancelNextItem] cancel next item : $item")

            playSynchronizer.releaseSyncImmediately(item, item.onReleaseCallback)
        }

        private fun handlePauseDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[handlePauseDirective] info : $info")
            setHandlingCompleted(info)
            executor.submit {
                executePause(PauseReason.BY_PAUSE_DIRECTIVE)
            }
        }

        private fun handleUnknownDirective(info: DirectiveInfo) {
            Logger.w(TAG, "[handleUnknownDirective] info: $info")
            removeDirective(info)
        }

        override fun cancelDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[cancelDirective] info: $info")
            cancelSync(info)
            removeDirective(info)
        }

        private fun cancelSync(info: DirectiveInfo) {
            val item = nextItem ?: return

            if (info.directive.getMessageId() == item.directive.getMessageId()) {
                playSynchronizer.releaseSyncImmediately(item, item.onReleaseCallback)
            }
        }

        private fun executeHandlePlayDirective(info: DirectiveInfo) {
            Logger.d(
                TAG,
                "[executeHandlePlayDirective] currentActivity:$currentActivity, focus: $focus"
            )
            if (!checkIfNextItemMatchWithInfo(info)) {
                Logger.d(TAG, "[executeHandlePlayDirective] skip")
                return
            }

            if (needStopCurrentPlayer(currentItem, nextItem)) {
                if (currentItem != null && currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
                    pauseReason =
                        PauseReason.BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY
                }
                executeStop(true)
            } else {
                if (currentItem != null && currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
                    pauseReason =
                        PauseReason.BY_PLAY_DIRECTIVE_FOR_RESUME
                }
            }

            if (FocusState.FOREGROUND != focus) {
                if (!focusManager.acquireChannel(
                        channelName,
                        this,
                        NAMESPACE,
                        nextItem?.playServiceId ?: ""
                    )
                ) {
                    progressTimer.stop()
                    sendPlaybackFailedEvent(
                        ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "Could not acquire $channelName for $NAMESPACE"
                    )
                }
                return
            } else {
                executePlayNextItem()
            }
        }

        private fun checkIfNextItemMatchWithInfo(info: DirectiveInfo): Boolean {
            val cacheNextItem = nextItem
            if (cacheNextItem == null) {
                Logger.e(TAG, "[checkIfNextItemMatchWithInfo] nextItem is null. maybe canceled")
                return false
            }

            Logger.d(
                TAG,
                "[checkIfNextItemMatchWithInfo] item message id: ${cacheNextItem.directive.getMessageId()}, directive message id : ${info.directive.getMessageId()}"
            )
            return cacheNextItem.directive.getMessageId() == info.directive.getMessageId()
        }

        private fun needStopCurrentPlayer(
            currentItem: AudioInfo?,
            nextItem: AudioInfo?
        ): Boolean {
            if (currentItem == null) {
                return false
            }

            if (nextItem == null) {
                return false
            }

            return currentItem.audioItem.stream.token != nextItem.audioItem.stream.token
        }

        private fun executeResume() {
            Logger.d(TAG, "[executeResume] currentActivity: $currentActivity")
            if (currentActivity == AudioPlayerAgentInterface.State.PAUSED && focus == FocusState.FOREGROUND) {
                if (!mediaPlayer.resume(sourceId)) {
                } else {
                }
            }
        }

        private fun executeStop(startNextSong: Boolean = false) {
            Logger.d(
                TAG,
                "[executeStop] currentActivity: $currentActivity, startNextSong: $startNextSong"
            )
            when (currentActivity) {
                AudioPlayerAgentInterface.State.IDLE,
                AudioPlayerAgentInterface.State.STOPPED,
                AudioPlayerAgentInterface.State.FINISHED -> {
                    if(playCalled) {
                        if(mediaPlayer.stop(sourceId)) {
                           stopCalled = true
                        }
                    }
                    return
                }
                AudioPlayerAgentInterface.State.PLAYING,
                AudioPlayerAgentInterface.State.PAUSED -> {
                    getOffsetInMilliseconds()
                    playNextItemAfterStopped = startNextSong
                    if (!mediaPlayer.stop(sourceId)) {

                    } else {
                        stopCalled = true
                    }
                }
            }
        }

        private fun executePause(reason: PauseReason) {
            Logger.d(TAG, "[executePause] currentActivity: $currentActivity")
            when (currentActivity) {
                AudioPlayerAgentInterface.State.IDLE,
                AudioPlayerAgentInterface.State.STOPPED,
                AudioPlayerAgentInterface.State.FINISHED -> return
                AudioPlayerAgentInterface.State.PAUSED -> {
                    pauseReason = reason
                }
                AudioPlayerAgentInterface.State.PLAYING -> {
                    getOffsetInMilliseconds()
                    if (!mediaPlayer.pause(sourceId)) {

                    } else {
                        pauseCalled = true
                        pauseReason = reason
                    }
                }
            }
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val audioNonBlockingPolicy = BlockingPolicy(
                BlockingPolicy.MEDIUM_AUDIO,
                false
            )

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[PLAY] = audioNonBlockingPolicy
            configuration[PAUSE] = audioNonBlockingPolicy
            configuration[STOP] = audioNonBlockingPolicy

            return configuration
        }

        override fun addListener(listener: AudioPlayerAgentInterface.Listener) {
            executor.submit {
                activityListeners.add(listener)
            }
        }

        override fun removeListener(listener: AudioPlayerAgentInterface.Listener) {
            executor.submit {
                activityListeners.remove(listener)
            }
        }

        override fun play() {
            onButtonPressed(PlaybackButton.PLAY)
        }

        override fun stop() {
            onButtonPressed(PlaybackButton.STOP)
        }

        override fun next() {
            onButtonPressed(PlaybackButton.NEXT)
        }

        override fun prev() {
            onButtonPressed(PlaybackButton.PREVIOUS)
        }

        override fun pause() {
            onButtonPressed(PlaybackButton.PAUSE)
        }

        override fun seek(millis: Long) {
            executor.submit {
                if (!sourceId.isError()) {
                    mediaPlayer.seekTo(sourceId, millis)
                }
            }
        }

        override fun getOffset(): Long = getOffsetInMilliseconds() / 1000L

        override fun getDuration(): Long = getDurationInMilliseconds() / 1000L

        private fun getOffsetInMilliseconds(): Long {
            if (!sourceId.isError()) {
                val offset = mediaPlayer.getOffset(sourceId)
                if (offset != MEDIA_PLAYER_INVALID_OFFSET) {
                    this.offset = offset
                }
            }

            return offset
        }

        private fun getDurationInMilliseconds(): Long {
            if (!sourceId.isError()) {
                val temp = mediaPlayer.getDuration(sourceId)
                if (temp != MEDIA_PLAYER_INVALID_OFFSET) {
                    duration = temp
                }
            }

            return duration
        }

        override fun onPlaybackStarted(id: SourceId) {
            Logger.d(TAG, "[onPlaybackStarted] id : $id")
            executor.submit {
                executeOnPlaybackStarted(id)
            }
        }

        private fun notifyOnActivityChanged() {
            currentItem?.let {
                val context = AudioPlayerAgentInterface.Context(
                    it.audioItem.stream.token,
                    it.audioItem.metaData?.template?.toString(),
                    getOffsetInMilliseconds()
                )
                activityListeners.forEach { listener ->
                    listener.onStateChanged(currentActivity, context)
                }
            }
        }

        private fun changeActivity(activity: AudioPlayerAgentInterface.State) {
            Logger.d(TAG, "[changeActivity] $currentActivity/$activity")
            currentActivity = activity
            executeProvideState(contextManager, namespaceAndName, 0, false)
            notifyOnActivityChanged()
        }

        override fun onPlaybackFinished(id: SourceId) {
            Logger.d(TAG, "[onPlaybackFinished] id : $id")
            executor.submit {
                executeOnPlaybackFinished(id)
            }
        }

        override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
            Logger.d(TAG, "[onPlaybackError] id : $id")
            executor.submit {
                executeOnPlaybackError(id, type, error)
            }
        }

        override fun onPlaybackPaused(id: SourceId) {
            Logger.d(TAG, "[onPlaybackPaused] id : $id")
            executor.submit {
                executeOnPlaybackPaused(id)
            }
        }

        override fun onPlaybackResumed(id: SourceId) {
            Logger.d(TAG, "[onPlaybackResumed] id : $id")
            executor.submit {
                executeOnPlaybackResumed(id)
            }
        }

        override fun onPlaybackStopped(id: SourceId) {
            Logger.d(TAG, "[onPlaybackStopped] id : $id")
            executor.submit {
                executeOnPlaybackStopped(id)
            }
        }

        override fun onFocusChanged(newFocus: FocusState) {
            Logger.d(TAG, "[onFocusChanged] newFocus : $newFocus")
            val wait = executor.submit {
                executeOnFocusChanged(newFocus)
            }

            // we have to stop playing player on background or none focus
            // So wait until player stopped.
            // We start playing on foreground, so don't need to wait.
            if (newFocus != FocusState.FOREGROUND) {
                wait.get()
            }
        }

        private fun executeOnPlaybackStarted(id: SourceId) {
            Logger.d(TAG, "[executeOnPlaybackStarted] id: $id, focus: $focus")
            playCalled = false
            executeOnPlaybackPlayingInternal(id)
            progressTimer.start()
            sendPlaybackStartedEvent()
        }

        private fun executeOnPlaybackResumed(id: SourceId) {
            Logger.d(TAG, "[executeOnPlaybackResumed] id: $id, focus: $focus")
            executeOnPlaybackPlayingInternal(id)
            progressTimer.resume()
            sendPlaybackResumedEvent()
        }

        private fun executeOnPlaybackPlayingInternal(id: SourceId) {
            if (id.id != sourceId.id) {
                return
            }

            // check focus state due to focus can be change after mediaPlayer.start().
            when (focus) {
                FocusState.FOREGROUND -> {
                }
                FocusState.BACKGROUND -> {
                    executeOnPlaybackPlayingOnBackgroundFocus()
                }
                FocusState.NONE -> {
                    executeOnPlaybackPlayingOnLostFocus()
                }
            }

            pauseReason = null
            playbackRouter.setHandler(this)
            changeActivity(AudioPlayerAgentInterface.State.PLAYING)
        }

        private fun executeOnPlaybackPlayingOnBackgroundFocus() {
            if (!mediaPlayer.pause(sourceId)) {
                Logger.e(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause failed")
            } else {
                Logger.d(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause Succeeded")
            }
        }

        private fun executeOnPlaybackPlayingOnLostFocus() {
            if (!mediaPlayer.stop(sourceId)) {
                Logger.e(TAG, "[executeOnPlaybackPlayingOnLostFocus] stop failed")
            } else {
                Logger.d(TAG, "[executeOnPlaybackPlayingOnLostFocus] pause Succeeded")
            }
        }

        private fun executeOnPlaybackPaused(id: SourceId) {
            if (id.id != sourceId.id) {
                return
            }

            pauseCalled = false
            changeActivity(AudioPlayerAgentInterface.State.PAUSED)
            progressTimer.pause()
            sendPlaybackPausedEvent()
        }

        private fun executeOnPlaybackError(id: SourceId, type: ErrorType, error: String) {
            Logger.d(TAG, "[executeOnPlaybackError]")
            if (id.id != sourceId.id) {
                return
            }

            sendPlaybackFailedEvent(type, error)
            progressTimer.stop()
            executeOnPlaybackStopped(sourceId, true)
        }

        private fun executeOnPlaybackStopped(id: SourceId, isError: Boolean = false) {
            Logger.d(TAG, "[executeOnPlaybackStopped] nextItem : $nextItem, $isError: $isError")
            if (id.id != sourceId.id) {
                Logger.e(TAG, "[executeOnPlaybackStopped] nextItem : $nextItem")
                return
            }

            stopCalled = false
            pauseReason = null
            when (currentActivity) {
                AudioPlayerAgentInterface.State.PLAYING,
                AudioPlayerAgentInterface.State.PAUSED -> {
                    changeActivity(AudioPlayerAgentInterface.State.STOPPED)
                    if (!isError) {
                        sendPlaybackStoppedEvent()
                    }
                    progressTimer.stop()

                    if (playNextItemAfterStopped) {
                        if (focus == FocusState.FOREGROUND && nextItem != null) {
                            executePlayNextItem()
                        }
                    } else {
                        if (nextItem == null) {
                            handlePlaybackCompleted(true)
                        }
                    }
                }
                AudioPlayerAgentInterface.State.IDLE,
                AudioPlayerAgentInterface.State.STOPPED,
                AudioPlayerAgentInterface.State.FINISHED -> {
                    if (focus != FocusState.NONE) {
                        handlePlaybackCompleted(true)
                    }
                }
            }
        }

        private fun executeOnPlaybackFinished(id: SourceId) {
            Logger.d(
                TAG,
                "[executeOnPlaybackFinished] id: $id , currentActivity: ${currentActivity.name}, nextItem: $nextItem"
            )
            if (id.id != sourceId.id) {
                Logger.e(
                    TAG,
                    "[executeOnPlaybackFinished] failed: invalidSourceId / $id, $sourceId"
                )
                return
            }

            pauseReason = null
            when (currentActivity) {
                AudioPlayerAgentInterface.State.PLAYING -> {
                    changeActivity(AudioPlayerAgentInterface.State.FINISHED)
                    progressTimer.stop()
                    sendPlaybackFinishedEvent()
                    if (nextItem == null) {
                        handlePlaybackCompleted(false)
                    } else {
                        executePlayNextItem()
                    }
                }
                else -> {

                }
            }
        }

        private fun handlePlaybackCompleted(byStop: Boolean) {
            Logger.d(TAG, "[handlePlaybackCompleted]")
            progressTimer.stop()

            val dialogRequestId = currentItem?.directive?.getDialogRequestId()
            if (dialogRequestId.isNullOrBlank()) {
                return
            }

            val syncObject = currentItem ?: return

            if (byStop) {
                playSynchronizer.releaseSyncImmediately(syncObject, syncObject.onReleaseCallback)
            } else {
                playSynchronizer.releaseSync(syncObject, syncObject.onReleaseCallback)
            }
        }

        private fun executeOnFocusChanged(newFocus: FocusState) {
            Logger.d(
                TAG,
                "[executeOnFocusChanged] focus: $newFocus, currentActivity: $currentActivity"
            )
            if (focus == newFocus) {
                return
            }

            focus = newFocus

            when (newFocus) {
                FocusState.FOREGROUND -> {
                    when (currentActivity) {
                        AudioPlayerAgentInterface.State.IDLE,
                        AudioPlayerAgentInterface.State.STOPPED,
                        AudioPlayerAgentInterface.State.FINISHED -> {
                            if (nextItem != null) {
                                executePlayNextItem()
                            }
                            return
                        }
                        AudioPlayerAgentInterface.State.PAUSED -> {
                            if (pauseReason == PauseReason.BY_PAUSE_DIRECTIVE || pauseReason == PauseReason.BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY) {
                                Logger.d(
                                    TAG,
                                    "[executeOnFocusChanged] skip resume, because player has been paused :$pauseReason."
                                )
                                return
                            }

                            if (pauseReason == PauseReason.BY_PLAY_DIRECTIVE_FOR_RESUME) {
                                Logger.d(
                                    TAG,
                                    "[executeOnFocusChanged] will be resume by next item"
                                )
                                executePlayNextItem()
                                return
                            }

                            if (!mediaPlayer.resume(sourceId)) {
                                focusManager.releaseChannel(channelName, this)
                                return
                            }
                            return
                        }
                        else -> {
                        }
                    }
                }
                FocusState.BACKGROUND -> {
                    when (currentActivity) {
                        AudioPlayerAgentInterface.State.STOPPED -> {
                            if (playNextItemAfterStopped && nextItem != null) {
                                playNextItemAfterStopped = false
                                return
                            }
                        }
                        AudioPlayerAgentInterface.State.FINISHED,
                        AudioPlayerAgentInterface.State.IDLE,
                        AudioPlayerAgentInterface.State.PAUSED,
                        AudioPlayerAgentInterface.State.PLAYING -> {
                            if (!sourceId.isError()) {
                                mediaPlayer.pause(sourceId)
                            }
                            return
                        }
                    }
                }
                FocusState.NONE -> {
                    when (currentActivity) {
                        AudioPlayerAgentInterface.State.PAUSED,
                        AudioPlayerAgentInterface.State.PLAYING -> {
                            executeCancelNextItem()
                            executeStop()
                            return
                        }
                    }
                    return
                }
            }
        }

        private fun executePlayNextItem() {
            progressTimer.stop()
            Logger.d(
                TAG,
                "[executePlayNextItem] nextItem: $nextItem, currentActivity: $currentActivity"
            )
            val currentPlayItem = nextItem
            if (currentPlayItem == null) {
                executeStop()
                return
            }

            currentPlayItem.let {
                currentItem?.let {
                    playSynchronizer.releaseSyncImmediately(it, it.onReleaseCallback)
                }
                currentItem = it
                nextItem = null
                if (!executeShouldResumeNextItem(token, it.audioItem.stream.token)) {
                    token = it.audioItem.stream.token
                    sourceId = mediaPlayer.setSource(URI.create(it.audioItem.stream.url))
                    if (sourceId.isError()) {
                        Logger.w(TAG, "[executePlayNextItem] failed to setSource")
                        executeOnPlaybackError(
                            sourceId,
                            ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                            "failed to setSource"
                        )
                        return
                    }

                    if (mediaPlayer.getOffset(sourceId) != it.audioItem.stream.offsetInMilliseconds) {
                        mediaPlayer.seekTo(sourceId, it.audioItem.stream.offsetInMilliseconds)
                    }

                    if (!mediaPlayer.play(sourceId)) {
                        Logger.w(TAG, "[executePlayNextItem] playFailed")
                        executeOnPlaybackError(
                            sourceId,
                            ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                            "playFailed"
                        )
                        return
                    }

                    playCalled = true

                    playSynchronizer.startSync(
                        it,
                        object : PlaySynchronizerInterface.OnRequestSyncListener {
                            override fun onGranted() {
                            }

                            override fun onDenied() {
                            }
                        })

                    progressTimer.init(
                        it.audioItem.stream.progressReport?.progressReportDelayInMilliseconds
                            ?: ProgressTimer.NO_DELAY,
                        it.audioItem.stream.progressReport?.progressReportIntervalInMilliseconds
                            ?: ProgressTimer.NO_INTERVAL, progressListener, progressProvider
                    )
                } else {
                    // Resume or Seek cases
                    if (mediaPlayer.getOffset(sourceId) != it.audioItem.stream.offsetInMilliseconds) {
                        mediaPlayer.seekTo(sourceId, it.audioItem.stream.offsetInMilliseconds)
                    }

                    if (currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
                        if (!mediaPlayer.resume(sourceId)) {
                            Logger.w(TAG, "[executePlayNextItem] resumeFailed")
                            executeOnPlaybackError(
                                sourceId,
                                ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                                "resumeFailed"
                            )
                            return
                        } else {
                            Logger.d(TAG, "[executePlayNextItem] resumeSucceeded")
                            playSynchronizer.startSync(
                                it,
                                object : PlaySynchronizerInterface.OnRequestSyncListener {
                                    override fun onGranted() {
                                    }

                                    override fun onDenied() {
                                    }
                                })
                        }
                    }
                }
            }
        }

        private fun executeShouldResumeNextItem(currentToken: String, nextToken: String): Boolean {
            return currentToken == nextToken && !sourceId.isError() && currentActivity.isActive()
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            info.result?.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
            info.result?.setFailed(msg)
            removeDirective(info)
        }

        private fun removeDirective(info: DirectiveInfo) {
            // Check result too, to catch cases where DirectiveInfo was created locally, without a nullptr result.
            // In those cases there is no messageId to remove because no result was expected.
            info.result?.let {
                removeDirective(info.directive.getMessageId())
            }
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            executor.submit {
                executeProvideState(contextSetter, namespaceAndName, stateRequestToken, true)
            }
        }

        private fun executeProvideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int,
            sendToken: Boolean
        ) {
            val policy = if (currentActivity == AudioPlayerAgentInterface.State.PLAYING) {
                StateRefreshPolicy.ALWAYS
            } else {
                StateRefreshPolicy.NEVER
            }

            contextSetter.setState(namespaceAndName, JsonObject().apply {
                addProperty("version", VERSION)
                addProperty("playerActivity", currentActivity.name)
                addProperty("token", token)
                addProperty("offsetInMilliseconds", getOffsetInMilliseconds())
                addProperty("durationInMilliseconds", getDurationInMilliseconds())
            }.toString(), policy, stateRequestToken)
        }

        private fun sendPlaybackStartedEvent() {
            sendEventWithOffset(EVENT_NAME_PLAYBACK_STARTED, offset)
        }

        private fun sendPlaybackFinishedEvent() {
            sendEventWithOffset(EVENT_NAME_PLAYBACK_FINISHED)
        }

        private fun sendPlaybackStoppedEvent() {
            sendEventWithOffset(EVENT_NAME_PLAYBACK_STOPPED)
        }

        private fun sendPlaybackPausedEvent() {
            sendEventWithOffset(EVENT_NAME_PLAYBACK_PAUSED)
        }

        private fun sendPlaybackResumedEvent() {
            sendEventWithOffset(EVENT_NAME_PLAYBACK_RESUMED)
        }

        private fun sendEventWithOffset(
            name: String,
            offset: Long = getOffsetInMilliseconds(),
            condition: () -> Boolean = { true }
        ) {
            sendEvent(name, offset, condition)
        }

        private fun sendPlaybackFailedEvent(type: ErrorType, errorMsg: String) {
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    currentItem?.apply {
                        val token = audioItem.stream.token
                        val messageRequest = EventMessageRequest(
                            UUIDGeneration.shortUUID().toString(),
                            UUIDGeneration.timeUUID().toString(),
                            jsonContext,
                            NAMESPACE,
                            EVENT_NAME_PLAYBACK_FAILED,
                            VERSION,
                            JsonObject().apply {
                                addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                                addProperty(KEY_TOKEN, token)
                                addProperty("offsetInMilliseconds", offset)

                                val error = JsonObject()
                                add("error", error)
                                with(error) {
                                    addProperty("type", type.name)
                                    addProperty("message", errorMsg)
                                }

                                val currentPlaybackState = JsonObject()
                                add("currentPlaybackState", currentPlaybackState)
                                with(currentPlaybackState) {
                                    addProperty("token", token)
                                    addProperty("offsetInMilliseconds", offset)
                                    addProperty("playActivity", currentActivity.name)
                                }
                            }.toString()
                        )

                        messageSender.sendMessage(messageRequest)
                    }
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                }
            })
        }

        private fun sendProgressReportDelay(actual: Long) {
            sendEvent(EVENT_NAME_PROGRESS_REPORT_DELAY_ELAPSED, actual) { true }
        }

        private fun sendProgressReportInterval(actual: Long) {
            sendEvent(EVENT_NAME_PROGRESS_REPORT_INTERVAL_ELAPSED, actual) { true }
        }

        private fun sendNextCommandIssued() {
            sendEventWithOffset(
                name = NAME_NEXT_COMMAND_ISSUED,
                condition = { currentActivity.isActive() })
        }

        private fun sendPreviousCommandIssued() {
            sendEventWithOffset(
                name = NAME_PREVIOUS_COMMAND_ISSUED,
                condition = { currentActivity.isActive() })
        }

        private fun sendPlayCommandIssued() {
            sendEventWithOffset(
                name = NAME_PLAY_COMMAND_ISSUED,
                condition = { currentActivity.isActive() })
        }

        private fun sendPauseCommandIssued() {
            sendEventWithOffset(
                name = NAME_PAUSE_COMMAND_ISSUED,
                condition = { currentActivity.isActive() })
        }

        private fun sendStopCommandIssued() {
            sendEventWithOffset(
                name = NAME_STOP_COMMAND_ISSUED,
                condition = { currentActivity.isActive() })
        }

        private fun sendEvent(eventName: String, offset: Long, condition: () -> Boolean) {
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    currentItem?.apply {
                        val token = audioItem.stream.token
                        val messageRequest = EventMessageRequest(
                            UUIDGeneration.shortUUID().toString(),
                            UUIDGeneration.timeUUID().toString(),
                            jsonContext,
                            NAMESPACE,
                            eventName,
                            VERSION,
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                                addProperty("token", token)
                                addProperty("offsetInMilliseconds", offset)
                            }.toString()
                        )

                        if (condition.invoke()) {
                            messageSender.sendMessage(messageRequest)
                            Logger.d(TAG, "[sendEvent] $messageRequest")
                        } else {
                            Logger.w(TAG, "[sendEvent] unsatisfied condition, so skip send.")
                        }
                    }
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                }
            })
        }

        override fun onButtonPressed(button: PlaybackButton) {
            executor.submit {
                Logger.w(TAG, "[onButtonPressed] button: $button, state : $currentActivity")
                if (!currentActivity.isActive()) {
                    Logger.w(TAG, "[onButtonPressed] not allowed in this state")
                    return@submit
                }

                when (button) {
                    PlaybackButton.PLAY -> {
                        executeResume()
//                    sendPlayCommandIssued()
                    }
                    PlaybackButton.PAUSE -> {
                        executePause(PauseReason.BY_PAUSE_DIRECTIVE)
//                    sendPauseCommandIssued()
                    }
                    PlaybackButton.STOP -> sendStopCommandIssued()
                    PlaybackButton.NEXT -> sendNextCommandIssued()
                    PlaybackButton.PREVIOUS -> sendPreviousCommandIssued()
                    else -> {
                        Logger.w(TAG, "[onButtonPressed] not supported -  $button")
                    }
                }
            }
        }

        override fun shutdown() {
            executor.submit {
                executeStop()
            }
        }
//    override fun onTogglePressed(toggle: PlaybackToggle, action: Boolean) {
//        Logger.w(TAG, "[onTogglePressed] not supported - $toggle, $action")
//    }

        private var displayAgent: DisplayAgentInterface? = null

        override fun setElementSelected(templateId: String, token: String) {
            displayAgent?.setElementSelected(templateId, token)
        }

        override fun displayCardRendered(templateId: String) {
            displayAgent?.displayCardRendered(templateId)
        }

        override fun displayCardCleared(templateId: String) {
            displayAgent?.displayCardCleared(templateId)
        }

        override fun setRenderer(renderer: DisplayAgentInterface.Renderer?) {
            displayAgent?.setRenderer(renderer)
        }

        internal fun setDisplayAgent(displayAgent: DisplayAgentInterface?) {
            this.displayAgent = displayAgent
        }
    }
}