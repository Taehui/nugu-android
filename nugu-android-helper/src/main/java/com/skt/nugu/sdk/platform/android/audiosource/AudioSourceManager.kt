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
package com.skt.nugu.sdk.platform.android.audiosource

import android.util.Log
import com.skt.nugu.sdk.client.audio.AudioInputStream
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provide audio source management.
 *
 * The Audio source exposed to client by [SharedDataStream] which can use multiple reader.
 *
 * And manage open/close state based on reference count.
 *
 * @param audioSourceFactory The audio source factory which can create [SharedDataStream]
 */
class AudioSourceManager(
    var audioSourceFactory: AudioSourceFactory
): AudioProvider {
    companion object {
        private const val TAG = "AudioSourceManager"
    }

    private val audioLock = ReentrantLock()
    //@GuardedBy("audioLock")
    private var audioSource: AudioSource? = null
    //@GuardedBy("audioLock")
    private var audioInputStream: SharedDataStream? = null
    //@GuardedBy("audioLock")
    private val consumerReferences = HashSet<Any>()

    private var writeThread: Thread? = null

    /**
     * Return [SharedDataStream] if already open or success to open.
     *
     * client should call [releaseAudioInputStream] with same [consumer] after using.
     *
     * @param consumer the consumer which will consume [audioInputStream]
     * @return [audioInputStream], if failed to create/open [AudioSource] return null
     */
    override fun acquireAudioInputStream(consumer: Any): SharedDataStream? {
        Log.d(TAG, "[acquireAudioInputStream] consumer: $consumer / consumers: ${consumerReferences.size}")
        audioLock.withLock {
            if (audioInputStream == null) {
                val openAudioSource = audioSourceFactory.create()
                if(!openAudioSource.open()) {
                    return null
                }
                audioSource = openAudioSource

                val openAudioInputStream =
                    AudioInputStream.open(audioSourceFactory.getFormat().sampleRateHz * 2 * 10)
                audioInputStream = openAudioInputStream

                writeThread = WriterThread(openAudioInputStream, openAudioSource).apply {
                    start()
                }
            }

            consumerReferences.add(consumer)
            return audioInputStream
        }
    }

    /**
     * Close [audioInputStream] if all [consumer] references released.
     *
     * @param consumer the consumer which has consumed [audioInputStream]
     */
    override fun releaseAudioInputStream(consumer: Any) {
        Log.d(TAG, "[releaseAudioInputStream] consumer: $consumer / consumers: ${consumerReferences.size}")
        audioLock.withLock {
            if (consumerReferences.isNotEmpty() && audioInputStream != null) {
                // only valid case
                consumerReferences.remove(consumer)
                if (consumerReferences.isEmpty()) {
                    closeResources()
                } else {

                }
            } else {
                Log.e(TAG, "[releaseAudioInputStream] not referenced consumer: $consumer")
            }
        }
    }

    private fun closeResources() {
        Log.d(TAG, "[closeResources]")
        audioSource?.close()
        audioSource = null
        audioInputStream = null
        writeThread = null
    }

    override fun getFormat(): AudioFormat = audioSourceFactory.getFormat()

    /**
     * Release all references and close stream.
     */
    fun reset() {
        audioLock.withLock {
            consumerReferences.clear()
            closeResources()
        }
    }

    private class WriterThread(
        private val audioInputStream: SharedDataStream,
        private val audioSource: AudioSource
    ) : Thread("WriterThread") {
        override fun run() {
            audioInputStream.createWriter().use {
                val temp = ByteArray(2048)
                while (true) {
                    val read = audioSource.read(temp, 0, temp.size)
                    if (read > 0) {
                        it.write(temp, 0, read)
                    } else {
                        Log.d(TAG, "[WriterThread] nothing to write")
                        return
                    }
                }
            }
        }
    }
}