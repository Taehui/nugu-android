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
package com.skt.nugu.sdk.core.interfaces.capability.tts

import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

abstract class AbstractTTSAgent(
    protected val speechPlayer: MediaPlayerInterface,
    protected val messageSender: MessageSender,
    protected val focusManager: FocusManagerInterface,
    protected val contextManager: ContextManagerInterface,
    protected val playSynchronizer: PlaySynchronizerInterface,
    protected val inputProcessorManager: InputProcessorManagerInterface,
    protected val channelName: String
) : CapabilityAgent()
    , ChannelObserver
    , DialogUXStateAggregatorInterface.Listener
    , TTSAgentInterface
    , InputProcessor {

    companion object {
        const val NAMESPACE = "TTS"
        const val VERSION = "1.0"
    }
}