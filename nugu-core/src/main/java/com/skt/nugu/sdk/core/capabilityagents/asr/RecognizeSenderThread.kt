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
package com.skt.nugu.sdk.core.capabilityagents.asr

import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.capabilityagents.impl.DefaultASRAgent
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.network.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration

abstract class RecognizeSenderThread(
    private val reader: SharedDataStream.Reader,
    private val inputFormat: AudioFormat,
    private val messageSender: MessageSender,
    private val observer: RecognizeSenderObserver,
    private val audioEncoder: Encoder
) : Thread() {
    companion object {
        private const val TAG = "RecognizeSenderThread"
    }

    @Volatile
    private var isStopping = false
    private var eventRequest: EventMessageRequest? = null
    private var currentAttachmentSequenceNumber: Int = 0

    override fun run() {
        try {
            Logger.d(TAG, "[run] start")
            if (!audioEncoder.startEncoding(inputFormat)) {
                observer.onError(Exception("Not Supported Input Format: $inputFormat"))
                return
            }

            val buffer = ByteArray(140 * inputFormat.getBytesPerMillis())
            var encodedBuffer: ByteArray?
            var read: Int

            eventRequest = sendRecognizeEvent()

            while (true) {
                if (isStopping) {
                    Logger.d(TAG, "[run] stop: isStopping is true")
                    break
                }

                if (reader.isClosed()) {
                    Logger.d(TAG, "[run] stop: reader closed")
                    break
                }
                // 1. read data
                read = reader.read(buffer, 0, buffer.size)

                // 2. encode data
                encodedBuffer = if (read > 0) {
                    audioEncoder.encode(buffer, 0, read)
                } else {
                    null
                }

                // 3. send data
                if (encodedBuffer != null) {
                    sendAttachment(encodedBuffer)
                }
            }

            if (isStopping) {
                sendStopRecognizeEvent()
                observer.onStop()
            } else {
                sendAttachment(null)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "[exception]", e)
            observer.onError(e)
        } finally {
            Logger.d(TAG, "[run] end")
            audioEncoder.stopEncoding()
            onFinished()
            reader.close()
        }
    }

    abstract fun onFinished()
    abstract fun createRecognizeEvent(): EventMessageRequest

    private fun sendRecognizeEvent(): EventMessageRequest = createRecognizeEvent().apply {
        Logger.d(TAG, "[sendRecognizeEvent] $this")
        messageSender.sendMessage(this)
    }

    private fun sendStopRecognizeEvent() {
        Logger.d(TAG, "[sendStopRecognizeEvent] $this")
        eventRequest?.let {
            messageSender.sendMessage(
                EventMessageRequest(
                    UUIDGeneration.shortUUID().toString(),
                    it.dialogRequestId,
                    it.context,
                    AbstractASRAgent.NAMESPACE,
                    DefaultASRAgent.EVENT_STOP_RECOGNIZE,
                    AbstractASRAgent.VERSION,
                    ""
                )
            )
        }
    }

    private fun sendAttachment(encoded: ByteArray?) {
        val request = eventRequest
        if (request != null) {
            val attachmentMessage = AttachmentMessageRequest(
                UUIDGeneration.shortUUID().toString(),
                request.dialogRequestId,
                request.context,
                AbstractASRAgent.NAMESPACE,
                DefaultASRAgent.NAME_RECOGNIZE,
                AbstractASRAgent.VERSION,
                currentAttachmentSequenceNumber,
                encoded == null,
                encoded
            )

            Logger.d(
                TAG,
                "[sendAttachment] $currentAttachmentSequenceNumber, ${encoded == null}, $this"
            )
            currentAttachmentSequenceNumber++
            messageSender.sendMessage(attachmentMessage)
        }
    }

    fun requestStop() {
        Logger.d(TAG, "[requestStop] $this")
        isStopping = true
        reader.close()
    }

    fun requestFinish() {
        if (isStopping) {
            Logger.d(TAG, "[requestFinish] skip: ($this) is Stopping")
            return
        }

        Logger.d(TAG, "[requestFinish] $this")
        reader.close()
    }
}