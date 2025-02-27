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
package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver

data class Channel(
    val name: String,
    val priority: Int,
    val volatile: Boolean = false
) : Comparator<Channel>, Comparable<Channel> {
    companion object {
        private const val TAG = "Channel"
    }

    data class State(
        val name: String,
        var focusState: FocusState = FocusState.NONE,
        var interfaceName: String = "",
        var playServiceId: String? = null
    )

    val state = State(name)
    private var observer: ChannelObserver? = null

    fun setFocus(focus: FocusState): Boolean {
        if (focus == state.focusState) {
            return false
        }

        state.focusState = focus
        observer?.onFocusChanged(state.focusState)

        if (FocusState.NONE == state.focusState) {
            observer = null
        }

        return true
    }

    fun setObserver(observer: ChannelObserver?) {
        this.observer = observer
    }

    fun hasObserver() = observer != null

    fun setInterfaceName(interfaceName: String) {
        state.interfaceName = interfaceName
    }

    fun setPlayerServiceId(playServiceId: String?) {
        state.playServiceId = playServiceId
    }

    fun getInterfaceName() = state.interfaceName

    fun getPlayServiceId() = state.playServiceId

    fun doesObserverOwnChannel(observer: ChannelObserver): Boolean = this.observer == observer

    override fun compare(o1: Channel, o2: Channel) = o2.priority - o1.priority

    override fun compareTo(other: Channel) = other.priority - priority
}