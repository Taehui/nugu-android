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
package com.skt.nugu.sdk.core.message

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header

object MessageFactory {
    private val gson = Gson()

    fun <T> create(json: String, classOfT: Class<T>): T? {
        return try {
            gson.fromJson(json, classOfT)
        } catch (e: Throwable) {
            null
        }
    }

    fun createHeader(jsonObject: JsonObject) = Header(
        jsonObject["dialogRequestId"].asString,
        jsonObject["messageId"].asString,
        jsonObject["name"].asString,
        jsonObject["namespace"].asString,
        jsonObject["version"].asString
    )

    fun createContent(jsonObject: JsonObject): ByteArray = gson.fromJson(jsonObject["bytes"].asJsonArray, ByteArray::class.java)

    fun createAttachmentMessage(json: JsonObject): AttachmentMessage? {
        return try {
            val content = createContent(json.getAsJsonObject("content"))
            val header = createHeader(json.getAsJsonObject("header"))
            val isEnd = json["isEnd"].asBoolean
            val parentMessageId = json["parentMessageId"].asString
            val seq = json["seq"].asInt

            AttachmentMessage(content, header, isEnd, parentMessageId, seq)
        } catch (e: Exception) {
            null
        }
    }

    fun createDirective(attachmentManager: AttachmentManagerInterface?, json: JsonObject): Directive? {
        return try {
            val header = createHeader(json.getAsJsonObject("header"))
            val payload = json.getAsJsonPrimitive("payload").asString
            Directive(
                attachmentManager,
                header,
                payload/*JsonParser().parse(payload).asJsonObject*/
            )
        } catch (e: Exception) {
            null
        }
    }

    fun createDirective(attachmentManager: AttachmentManagerInterface?, header: Header, payload: JsonObject): Directive? =
        Directive(attachmentManager, header, payload.toString())
}