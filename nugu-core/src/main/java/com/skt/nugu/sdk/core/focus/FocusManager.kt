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
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.context.PlayStackProvider
import com.skt.nugu.sdk.core.utils.DequeSingleThreadExecutor
import java.util.*
import java.util.concurrent.*
import kotlin.collections.LinkedHashSet

class FocusManager(
    channelConfigurations: List<FocusManagerInterface.ChannelConfiguration>,
    tagHint: String? = null
) : FocusManagerInterface, PlayStackProvider {


    private val TAG = if (tagHint.isNullOrBlank()) {
        "FocusManager"
    } else {
        "FocusManager_$tagHint"
    }

    private val allChannels: MutableMap<String, Channel> = HashMap()

    /**
     * activeChannels must be thread safe.
     * Do synchronize when use.
     */
    //@GuardedBy("activeChannels")
    private val activeChannels = TreeSet<Channel>()
    private val executor = DequeSingleThreadExecutor()

    private val listeners = CopyOnWriteArraySet<FocusManagerInterface.OnFocusChangedListener>()

    init {
        for (channelConfiguration in channelConfigurations) {
            allChannels[channelConfiguration.name] =
                Channel(channelConfiguration.name, channelConfiguration.priority, channelConfiguration.volatile)
        }
    }

    override fun acquireChannel(
        channelName: String,
        channelObserver: ChannelObserver,
        interfaceName: String,
        playServiceId: String?
    ): Boolean {
        val channelToAcquire = getChannel(channelName)

        if (channelToAcquire == null) {
            return false
        }

        executor.submit {
            acquireChannelHelper(channelToAcquire, channelObserver, interfaceName, playServiceId)
        }

        return true
    }

    override fun releaseChannel(channelName: String, channelObserver: ChannelObserver): Future<Boolean> {
        Logger.d(TAG, "[releaseChannel] ${channelName}")
        val channelToRelease = getChannel(channelName)

        val returnValue = object : Future<Boolean> {
            override fun isDone(): Boolean = true
            override fun get(): Boolean = false
            override fun get(timeout: Long, unit: TimeUnit?): Boolean = false
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
            override fun isCancelled(): Boolean = false
        }

        if (channelToRelease == null) {
            return returnValue
        }

        return executor.submit(Callable<Boolean> {
            releaseChannelHelper(
                channelToRelease,
                channelObserver,
                channelName
            )
        })
    }

    override fun stopForegroundActivity() {
        val foregroundChannel = getHighestPriorityActiveChannel()
        if (foregroundChannel == null) {
            return
        }

        executor.submitFirst {
            stopForegroundActivityHelper(foregroundChannel, foregroundChannel.getInterfaceName())
        }
    }

    private fun acquireChannelHelper(
        channelToAcquire: Channel,
        channelObserver: ChannelObserver,
        interfaceName: String,
        playServiceId: String?
    ) {
        val shouldReleaseChannelFocus =
            !(activeChannels.contains(channelToAcquire) && channelToAcquire.getInterfaceName() == interfaceName)

        if (shouldReleaseChannelFocus) {
            setChannelFocus(channelToAcquire, FocusState.NONE)
        }

        val foregroundChannel = getHighestPriorityActiveChannel()
        Logger.d(
            TAG,
            "[acquireChannelHelper] ${channelToAcquire.name}, $interfaceName , foreground: ${foregroundChannel?.name}, $playServiceId"
        )
        channelToAcquire.setInterfaceName(interfaceName)
        channelToAcquire.setPlayerServiceId(playServiceId)
        synchronized(activeChannels) {
            activeChannels.add(channelToAcquire)
        }

        channelToAcquire.setObserver(channelObserver)

        when {
            foregroundChannel == null -> setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
            foregroundChannel == channelToAcquire -> setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
            channelToAcquire > foregroundChannel || channelToAcquire.volatile || foregroundChannel.volatile -> {
                setChannelFocus(foregroundChannel, FocusState.BACKGROUND)
                setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
            }
            else -> {
                setChannelFocus(channelToAcquire, FocusState.BACKGROUND)
            }
        }
    }

    private fun releaseChannelHelper(
        channelToRelease: Channel,
        channelObserver: ChannelObserver,
        channelName: String
    ): Boolean {
        Logger.d(TAG, "[releaseChannelHelper] ${channelToRelease.name}, ${channelToRelease.state.interfaceName}")
        if (!channelToRelease.doesObserverOwnChannel(channelObserver)) {
            return false
        }

        val wasForegrounded = isChannelForegrounded(channelToRelease)
        synchronized(activeChannels) {
            activeChannels.remove(channelToRelease)
        }

        setChannelFocus(channelToRelease, FocusState.NONE)
        if (wasForegrounded) {
            foregroundHighestPriorityActiveChannel()
        }
        return true
    }

    private fun stopForegroundActivityHelper(foregroundChannel: Channel, foregroundChannelInterfaceName: String) {
        if (foregroundChannelInterfaceName != foregroundChannel.getInterfaceName()) {
            return
        }

        if (foregroundChannel.hasObserver() == false) {
            return
        }

        setChannelFocus(foregroundChannel, FocusState.NONE)

        synchronized(activeChannels) {
            activeChannels.remove(foregroundChannel)
        }
        foregroundHighestPriorityActiveChannel()
    }

    private fun setChannelFocus(channel: Channel, focus: FocusState) {
        if (channel.setFocus(focus) == false) {
            return
        }

        listeners.forEach { it.onFocusChanged(channel.name, focus, channel.state.interfaceName) }
    }

    private fun getChannel(channelName: String): Channel? = allChannels[channelName]

    private fun getHighestPriorityActiveChannel(): Channel? =
        synchronized(activeChannels) { activeChannels.lastOrNull() }

    private fun isChannelForegrounded(channelToRelease: Channel) = getHighestPriorityActiveChannel() == channelToRelease

    private fun foregroundHighestPriorityActiveChannel() {
        val channelToForeground = getHighestPriorityActiveChannel()
        Logger.d(TAG, "[foregroundHighestPriorityActiveChannel] ${channelToForeground?.name}")
        if (channelToForeground != null) {
            setChannelFocus(channelToForeground, FocusState.FOREGROUND)
        } else {
            Logger.d(TAG, "[foregroundHighestPriorityActiveChannel] non channel to foreground.")
        }
    }

    override fun addListener(listener: FocusManagerInterface.OnFocusChangedListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: FocusManagerInterface.OnFocusChangedListener) {
        listeners.remove(listener)
    }

    override fun getPlayStack(): Stack<String> {
        val set = LinkedHashSet<String>().apply {
            synchronized(activeChannels) {
                activeChannels.forEach {
                    it.getPlayServiceId()?.let { playServiceId ->
                        add(playServiceId)
                    }
                }
            }
        }

        return Stack<String>().apply {
            set.forEach {
                push(it)
            }
        }
    }
}