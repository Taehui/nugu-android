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
package com.skt.nugu.sdk.core.sds

import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.utils.Logger
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class CircularBufferSharedDataStream(private val capacity: Int) : SharedDataStream {
    companion object {
        private const val TAG = "CircularBufferSharedDataStream"

        const val OVERRUN = -2
        const val UNDERRUN = -1
    }

    private interface BufferEventListener {
        fun onBufferFilled()
        fun onBufferFullFilled()
    }

    private val buffer = ByteArray(capacity)
    private var writeOffset: Int = 0
    private var fillCount: Long = 0L
    private val lock = ReentrantReadWriteLock()
    private val bufferEventListeners = HashSet<BufferEventListener>()

    // this is for debug
    @Volatile
    private var readerCount = 0
    @Volatile
    private var writerCount = 0

    // get absolute position at stream
    override fun getPosition(): Long = fillCount * capacity + writeOffset

    override fun createWriter(): SharedDataStream.Writer {
        writerCount++
        Logger.d(TAG, "[createWriter] $writerCount")
        return MyWriter()
    }

    override fun createReader(initialPosition: Long?): SharedDataStream.Reader {
        readerCount++
        Logger.d(TAG, "[createReader] $readerCount")
        return if (initialPosition == null) {
            MyReader()
        } else {
            MyReader(initialPosition)
        }
    }

    private inner class MyWriter : SharedDataStream.Writer {
        private val TAG = "MyWriter"
        @Volatile
        private var isClosing = false

        override fun write(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int) {
            var currentOffsetInBytes = offsetInBytes
            var leftSizeInBytes = sizeInBytes

            while (leftSizeInBytes > 0) {
                val writableBufferSizeAtOnce = capacity - writeOffset
                val writeSizeInBytes = Math.min(leftSizeInBytes, writableBufferSizeAtOnce)

                lock.write {
                    System.arraycopy(bytes, currentOffsetInBytes, buffer, writeOffset, writeSizeInBytes)

                    writeOffset += writeSizeInBytes

                    if (writeOffset == capacity) {
                        writeOffset = 0
                        fillCount++
                    }
                }

                leftSizeInBytes -= writeSizeInBytes
                currentOffsetInBytes += writeSizeInBytes

                notifyBufferFilled()
            }
        }

        override fun close() {
            if(isClosing) {
                return
            }
            isClosing = true
            writerCount--
            Logger.d(TAG, "[close] $writerCount")
            notifyBufferFullFilled()
        }
    }

    private fun notifyBufferFilled() {
        synchronized(bufferEventListeners) {
            bufferEventListeners.forEach {
                it.onBufferFilled()
            }
        }
    }

    private fun notifyBufferFullFilled() {
        synchronized(bufferEventListeners) {
            bufferEventListeners.forEach {
                it.onBufferFullFilled()
            }
        }
    }

    private inner class MyReader(
        private var readPosition: Long = getPosition()
    ) : SharedDataStream.Reader, BufferEventListener {
        private val TAG = "MyReader"
        @Volatile
        private var isClosing = false
        private var isReading = false

        @Volatile
        private var isBufferFullFilled = false

        private val waitLock = Object()

        init {
            synchronized(bufferEventListeners) {
                bufferEventListeners.add(this)
            }
        }

        override fun read(byteBuffer: ByteBuffer, offsetInBytes: Int, sizeInBytes: Int): Int {
            byteBuffer.position(offsetInBytes)

            return readInternal(offsetInBytes, sizeInBytes) { readOffset, _, readSize ->
                byteBuffer.put(buffer, readOffset, readSize)
            }
        }

        override fun read(dstBytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
            return readInternal(offsetInBytes, sizeInBytes) { readOffset, dstOffsetInBytes, readSize ->
                System.arraycopy(buffer, readOffset, dstBytes, dstOffsetInBytes, readSize)
            }
        }

        private fun readInternal(offsetInBytes: Int, sizeInBytes: Int, readFunction: (Int, Int, Int) -> Unit): Int {
            var dstOffsetInBytes = offsetInBytes
            var leftSizeInBytes = sizeInBytes

            while (leftSizeInBytes > 0 && !isClosing) {
                isReading = true
                var read = 0
                lock.read {
                    val currentIndex = getPosition()
                    val readableBufferSizeInBytes = currentIndex - readPosition
                    read = when {
                        readableBufferSizeInBytes > capacity -> // 읽으려는 Index가 overwrite 됨.
                            OVERRUN
                        readableBufferSizeInBytes > 0 -> {
                            // can read
                            val readOffset = (readPosition % capacity).toInt()
                            val readSize = Math.min(
                                capacity - readOffset,
                                Math.min(readableBufferSizeInBytes.toInt(), leftSizeInBytes)
                            )

                            readFunction.invoke(readOffset, dstOffsetInBytes, readSize)
                            readSize
                        }
                        else -> {
                            // 읽을 데이터가 하나도 준비되있지 않음. (UNDERRUN)
                            0
                        }
                    }

                    if (read == OVERRUN) {
                        isReading = false
                        return OVERRUN
                    }

                    if (read > 0) {
                        leftSizeInBytes -= read
                        dstOffsetInBytes += read
                        readPosition += read
//                        Logger.d(TAG, "[read] position: $readPosition ")
                    }
                }

                if (read == 0 && !isClosing) {
                    synchronized(waitLock) {
                        waitLock.wait(50L)
                    }

                    if(isBufferFullFilled) {
                        isReading = false
                        return 0
                    }
                }
            }

            isReading = false
            return sizeInBytes - leftSizeInBytes
        }

        override fun position(): Long = readPosition

        override fun close() {
            if (!isClosing) {
                readerCount--
                synchronized(bufferEventListeners) {
                    bufferEventListeners.remove(this)
                }
                isClosing = true
                Logger.d(TAG, "[close] $this, readerCount : $readerCount")
            }
            wakeLock()
        }

        override fun isClosed(): Boolean {
            return !isReading && isClosing
        }

        override fun onBufferFilled() {
            wakeLock()
        }

        override fun onBufferFullFilled() {
            isBufferFullFilled = true
            wakeLock()
        }

        private fun wakeLock() {
            synchronized(waitLock) {
                waitLock.notifyAll()
            }
        }
    }
}