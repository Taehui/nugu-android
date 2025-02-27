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

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.HashMap
import java.util.concurrent.Executors

object DefaultExtensionAgent {
    private const val TAG = "DefaultExtensionAgent"

    val FACTORY = object : ExtensionAgentFactory {
        override fun create(
            contextManager: ContextManagerInterface,
            messageSender: MessageSender
        ): AbstractExtensionAgent =
            Impl(
                contextManager,
                messageSender
            )
    }

    internal data class ExtensionPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("data")
        val data: JsonObject
    )

    internal class Impl constructor(
        contextManager: ContextManagerInterface,
        messageSender: MessageSender
    ) : AbstractExtensionAgent(contextManager, messageSender) {
        companion object {
            private const val NAME_ACTION = "Action"
            private const val NAME_ACTION_SUCCEEDED = "ActionSucceeded"
            private const val NAME_ACTION_FAILED = "ActionFailed"

            private val ACTION = NamespaceAndName(NAMESPACE,
                NAME_ACTION
            )
            private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"
            private const val PAYLOAD_DATA = "data"
        }

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)
        private val executor = Executors.newSingleThreadExecutor()

        private var client: ExtensionAgentInterface.Client? = null

        init {
            contextManager.setStateProvider(namespaceAndName, this)
            contextManager.setState(namespaceAndName, buildContext(), StateRefreshPolicy.NEVER)
        }

        override fun setClient(client: ExtensionAgentInterface.Client) {
            this.client = client
        }

        private fun buildContext(): String = JsonObject().apply {
            addProperty("version", VERSION)
        }.toString()

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            contextSetter.setState(
                namespaceAndName,
                buildContext(),
                StateRefreshPolicy.NEVER,
                stateRequestToken
            )
        }

        override fun preHandleDirective(info: DirectiveInfo) {
        }

        override fun handleDirective(info: DirectiveInfo) {
            when (info.directive.getName()) {
                NAME_ACTION -> handleActionDirective(info)
            }
        }

        private fun handleActionDirective(info: DirectiveInfo) {
            val payload = MessageFactory.create(info.directive.payload, ExtensionPayload::class.java)
            if(payload == null) {
                Logger.d(TAG, "[handleActionDirective] invalid payload: ${info.directive.payload}")
                setHandlingFailed(info, "[handleActionDirective] invalid payload: ${info.directive.payload}")
                return
            }

            val data = payload.data
            val playServiceId = payload.playServiceId

            executor.submit {
                val currentClient = client
                if (currentClient != null) {
                    if (currentClient.action(data.toString(), playServiceId)) {
                        sendActionSucceededEvent(playServiceId)
                    } else {
                        sendActionFailedEvent(playServiceId)
                    }
                } else {
                    Logger.w(
                        TAG,
                        "[handleActionDirective] no current client. set client using setClient()."
                    )
                }
            }
            setHandlingCompleted(info)
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            info.result?.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, description: String) {
            info.result?.setFailed(description)
            removeDirective(info)
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[ACTION] = nonBlockingPolicy

            return configuration
        }

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }

        private fun sendActionSucceededEvent(playServiceId: String) {
            sendEvent(NAME_ACTION_SUCCEEDED, playServiceId)
        }

        private fun sendActionFailedEvent(playServiceId: String) {
            sendEvent(NAME_ACTION_FAILED, playServiceId)
        }

        private fun sendEvent(name: String, playServiceId: String) {
            Logger.d(TAG, "[sendEvent] name: $name, playServiceId: $playServiceId")
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    val request = EventMessageRequest(
                        UUIDGeneration.shortUUID().toString(),
                        UUIDGeneration.timeUUID().toString(),
                        jsonContext,
                        NAMESPACE,
                        name,
                        VERSION,
                        JsonObject().apply {
                            addProperty(PAYLOAD_PLAY_SERVICE_ID, playServiceId)
                        }.toString()
                    )

                    messageSender.sendMessage(request)
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                }
            })
        }
    }
}