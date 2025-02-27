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
package com.skt.nugu.sdk.core.interfaces.capability.system

/**
 * The public interface for SystemCapabilityAgent
 */
interface SystemCapabilityAgentInterface {
    /**
     * The function to be called when the user has become active.
     */
    fun onUserActive()
    /**
     * The function to be called on shutdown.
     */
    fun onUserDisconnect()
    /**
     * @hide
     * internal function
     * **/
    fun onEcho()
    /**
     * Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: SystemCapabilityAgentListener)
    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: SystemCapabilityAgentListener)
}