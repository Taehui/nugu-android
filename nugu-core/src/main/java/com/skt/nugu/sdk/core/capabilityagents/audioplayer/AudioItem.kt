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
package com.skt.nugu.sdk.core.capabilityagents.audioplayer

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.UUIDGeneration

data class AudioItem(
    @SerializedName("stream")
    val stream: Stream,
    @SerializedName("metadata")
    val metaData: Metadata?
) {
    /**
     *
     */
    data class Stream(
        @SerializedName("url")
        val url: String,
        @SerializedName("offsetInMilliseconds")
        val offsetInMilliseconds: Long,
        @SerializedName("progressReport")
        val progressReport: ProgressReport?,
        @SerializedName("token")
        val token: String,
        @SerializedName("expectedPreviousToken")
        val expectedPreviousToken: String
    )

    data class Metadata(
        @SerializedName("disableTemplate")
        val disableTemplate: Boolean?,
        @SerializedName("template")
        val template: JsonObject?
    ) {
        fun asDisplayDirective(dialogRequestId: String, playServiceId: String): Directive? {
            if (template == null) {
                return null
            }

            template.addProperty("playServiceId", playServiceId)

            return MessageFactory.createDirective(null, Header(
                dialogRequestId,
                UUIDGeneration.shortUUID().toString(),
                template.getAsJsonPrimitive("type").asString,
                AbstractDisplayAgent.NAMESPACE,
                AbstractDisplayAgent.VERSION
            ), template)
        }
    }

    data class ProgressReport(
        @SerializedName("progressReportDelayInMilliseconds")
        val progressReportDelayInMilliseconds: Long?,
        @SerializedName("progressReportIntervalInMilliseconds")
        val progressReportIntervalInMilliseconds: Long?
    )
}