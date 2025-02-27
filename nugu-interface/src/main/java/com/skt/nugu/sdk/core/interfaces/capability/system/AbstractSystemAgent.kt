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

import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface

abstract class AbstractSystemAgent (
    protected val messageSender: MessageSender,
    protected val connectionManager: ConnectionManagerInterface,
    protected val contextManager: ContextManagerInterface,
    protected val authDelegate: AuthDelegate,
    protected val batteryStatusProvider: BatteryStatusProvider? = null
) : CapabilityAgent(),
    ConnectionStatusListener, SystemCapabilityAgentInterface, ContextRequester {
    companion object {
        const val NAMESPACE = "System"
        const val VERSION = "1.0"
    }

    abstract fun shutdown()
}