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
package com.skt.nugu.sdk.core.interfaces.connection

import com.skt.nugu.sdk.core.interfaces.message.MessageObserver

/**
 * This class reflects a connection to DeviceGateway and how it may be observed.
 */
interface ConnectionManagerInterface : NetworkManagerInterface {
    /**
     * Enable connection
     */
    override fun enable()
    /**
     * Disable connection
     */
    override fun disable()

    /**
     * check whether a connection
     */
    fun isEnabled(): Boolean

    /**
     * reconnect to DeviceGateway.
     */
    fun reconnect()

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    fun isConnected(): Boolean
    /**
     * the device to disconnect and connect
     * Registry Connection Handoff from SystemCapabilityAgent#handleHandoffConnection
     * @param protocol is only H2
     * @param domain is address
     * @param hostname is ipaddress
     * @param port the port
     * @param retryCountLimit Maximum count of retries
     * @param charge (internal option)
     */
    fun handoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    )
    /**
     * Adds an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to add.
     */
    fun addMessageObserver(observer: MessageObserver)

    /**
     * Removes an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to remove.
     */
    fun removeMessageObserver(observer: MessageObserver)

    /**
     * Add listener to be notified when connection status changed for DeviceGateway
     * @param listener the listener that will add
     */
    override fun addConnectionStatusListener(listener: ConnectionStatusListener)
    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    override fun removeConnectionStatusListener(listener: ConnectionStatusListener)
}