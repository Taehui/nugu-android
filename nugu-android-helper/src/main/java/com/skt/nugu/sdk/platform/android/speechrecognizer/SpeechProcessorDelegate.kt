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
package com.skt.nugu.sdk.platform.android.speechrecognizer

import android.util.Log
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import java.util.concurrent.Future

/**
 * This class delegate SpeechProcessor to NUGU SDK to avoid conflict with NUGU SDK.
 */
class SpeechProcessorDelegate(
    private val asrAgent: ASRAgentInterface
) {

    companion object {
        // To remove error, shorten TAG
//        const val TAG = "SpeechProcessorDelegate"
        private const val TAG = "SpeechProcessorD"
    }

    private var epdState = AudioEndPointDetector.State.STOP
    private val listeners = HashMap<AudioEndPointDetector.OnStateChangedListener, ASRAgentInterface.OnStateChangeListener>()

    /**
     * Request starting epd to [ASRAgentInterface]
     */
    fun start(audioInputStream: SharedDataStream?, audioFormat: AudioFormat?, wakewordStartPosition: Long?, wakewordEndPosition: Long?): Future<Boolean> {
        Log.d(TAG, "[startDetector]")
        return asrAgent.startRecognition(audioInputStream, audioFormat, wakewordStartPosition, wakewordEndPosition)
    }

    /**
     * Request stop epd to [ASRAgentInterface]
     */
    fun stop() {
        asrAgent.stopRecognition()
    }

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        object : ASRAgentInterface.OnStateChangeListener {
            override fun onStateChanged(state: ASRAgentInterface.State) {
                if(!epdState.isActive() && !state.isRecognizing()) {
                    Log.d(
                        TAG,
                        "[AudioEndPointDetectorStateObserverInterface] invalid state change : $epdState / $state"
                    )
                    return
                }

                if(state != ASRAgentInterface.State.EXPECTING_SPEECH) {
                    epdState = when (state) {
                        ASRAgentInterface.State.IDLE -> AudioEndPointDetector.State.STOP
//                    ASRAgentListener.State.TIMEOUT -> AudioEndPointDetector.State.TIMEOUT
                        ASRAgentInterface.State.EXPECTING_SPEECH,
                        ASRAgentInterface.State.LISTENING -> AudioEndPointDetector.State.EXPECTING_SPEECH
                        ASRAgentInterface.State.RECOGNIZING -> AudioEndPointDetector.State.SPEECH_START
                        ASRAgentInterface.State.BUSY -> AudioEndPointDetector.State.SPEECH_END
                    }

                    listener.onStateChanged(epdState)
                }
            }
        }.apply {
            listeners[listener] = this
            asrAgent.addOnStateChangeListener(this)
        }
    }

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        listeners.remove(listener)?.apply {
            asrAgent.removeOnStateChangeListener(this)
        }
    }
}