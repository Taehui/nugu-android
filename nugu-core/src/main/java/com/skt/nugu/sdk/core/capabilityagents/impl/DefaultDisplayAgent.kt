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
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.capabilityagents.display.BaseDisplayAgent
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import java.util.HashMap

object DefaultDisplayAgent {
    private const val TAG = "DisplayTemplateAgent"

    val FACTORY = object : DisplayAgentFactory {
        override fun create(
            focusManager: FocusManagerInterface,
            contextManager: ContextManagerInterface,
            messageSender: MessageSender,
            playSynchronizer: PlaySynchronizerInterface,
            channelName: String
        ): AbstractDisplayAgent = Impl(
            focusManager,
            contextManager,
            messageSender,
            playSynchronizer,
            channelName
        )
    }

    internal class Impl(
        focusManager: FocusManagerInterface,
        contextManager: ContextManagerInterface,
        messageSender: MessageSender,
        playSynchronizer: PlaySynchronizerInterface,
        channelName: String
    ) : BaseDisplayAgent(focusManager, contextManager, messageSender, playSynchronizer, channelName) {
        companion object {
            const val NAMESPACE = "Display"
            const val VERSION = "1.0"

            private const val NAME_FULLTEXT1 = "FullText1"
            private const val NAME_FULLTEXT2 = "FullText2"
            private const val NAME_IMAGETEXT1 = "ImageText1"
            private const val NAME_IMAGETEXT2 = "ImageText2"
            private const val NAME_IMAGETEXT3 = "ImageText3"
            private const val NAME_IMAGETEXT4 = "ImageText4"
            private const val NAME_TEXTLIST1 = "TextList1"
            private const val NAME_TEXTLIST2 = "TextList2"
            private const val NAME_IMAGELIST1 = "ImageList1"
            private const val NAME_IMAGELIST2 = "ImageList2"
            private const val NAME_CUSTOMTEMPLATE = "CustomTemplate"

            private val FULLTEXT1 = NamespaceAndName(
                NAMESPACE,
                NAME_FULLTEXT1
            )
            private val FULLTEXT2 = NamespaceAndName(
                NAMESPACE,
                NAME_FULLTEXT2
            )
            private val IMAGETEXT1 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGETEXT1
            )
            private val IMAGETEXT2 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGETEXT2
            )
            private val IMAGETEXT3 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGETEXT3
            )
            private val IMAGETEXT4 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGETEXT4
            )
            private val TEXTLIST1 = NamespaceAndName(
                NAMESPACE,
                NAME_TEXTLIST1
            )
            private val TEXTLIST2 = NamespaceAndName(
                NAMESPACE,
                NAME_TEXTLIST2
            )
            private val IMAGELIST1 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGELIST1
            )
            private val IMAGELIST2 = NamespaceAndName(
                NAMESPACE,
                NAME_IMAGELIST2
            )
            private val CUSTOM_TEMPLATE = NamespaceAndName(
                NAMESPACE,
                NAME_CUSTOMTEMPLATE
            )
        }

        init {
            contextManager.setStateProvider(namespaceAndName, this)
        }

        override fun getDisplayType(): DisplayAggregatorInterface.Type = DisplayAggregatorInterface.Type.INFOMATION

        override fun executeOnFocusBackground(info: DirectiveInfo) {
            getRenderer()?.clear(info.directive.getMessageId(), true)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()
            configuration[FULLTEXT1] = nonBlockingPolicy
            configuration[FULLTEXT2] = nonBlockingPolicy
            configuration[IMAGETEXT1] = nonBlockingPolicy
            configuration[IMAGETEXT2] = nonBlockingPolicy
            configuration[IMAGETEXT3] = nonBlockingPolicy
            configuration[IMAGETEXT4] = nonBlockingPolicy
            configuration[TEXTLIST1] = nonBlockingPolicy
            configuration[TEXTLIST2] = nonBlockingPolicy
            configuration[IMAGELIST1] = nonBlockingPolicy
            configuration[IMAGELIST2] = nonBlockingPolicy
            configuration[CUSTOM_TEMPLATE] = nonBlockingPolicy

            return configuration
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            executor.submit {
                contextSetter.setState(
                    namespaceAndName,
                    buildContext(currentInfo),
                    StateRefreshPolicy.ALWAYS,
                    stateRequestToken
                )
            }
        }

        private fun buildContext(info: TemplateDirectiveInfo?): String = JsonObject().apply {
            addProperty("version",
                VERSION
            )
            info?.payload?.let {
                addProperty("playServiceId", it.playServiceId)
                addProperty("token", it.token)
            }
        }.toString()
    }
}