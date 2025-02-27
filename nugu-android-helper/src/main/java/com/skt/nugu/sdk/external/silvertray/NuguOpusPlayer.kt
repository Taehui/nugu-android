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
package com.skt.nugu.sdk.external.silvertray

import android.util.Log
import com.skt.nugu.silvertray.player.EventListener
import com.skt.nugu.silvertray.player.Player
import com.skt.nugu.silvertray.player.Status
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.mediaplayer.*

/**
 * Porting class silvertray's [Player] to use in NUGU SDK
 */
class NuguOpusPlayer(private val streamType: Int) : AttachmentPlayablePlayer {
    companion object {
        private const val TAG = "NuguOpusPlayer"
    }

    private val player = Player()
    private var currentSourceId: SourceId = SourceId.ERROR()
    private var status = Status.IDLE
    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null

    init {
        player.addListener(object : EventListener {
            override fun onError(message: String) {
                playbackEventListener?.onPlaybackError(currentSourceId, ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR, message)
            }

            override fun onStatusChanged(status: Status) {
                Log.d(TAG, "[onStatusChanged] status: $status")
                handleStatusChanged(status)
            }
        })
    }

    private fun handleStatusChanged(status: Status) {
        val prevStatus = this.status
        this.status = status
        val listener = this.playbackEventListener ?: return
        when (status) {
            Status.IDLE -> {
                listener.onPlaybackStopped(currentSourceId)
            }
            Status.STARTED -> {
                if (prevStatus == Status.PAUSED) {
                    listener.onPlaybackResumed(currentSourceId)
                } else {
                    listener.onPlaybackStarted(currentSourceId)
                }
            }
            Status.PAUSED -> {
                listener.onPlaybackPaused(currentSourceId)
            }
            Status.ENDED -> {
                listener.onPlaybackFinished(currentSourceId)
            }
            else -> {
            }
        }
    }

    override fun setSource(attachmentReader: Attachment.Reader): SourceId {
        val source = RawCBRStreamSource(attachmentReader)
        player.prepare(source, streamType)
        currentSourceId.id++
        Log.d(TAG, "[setSource] ${currentSourceId.id}")
        return currentSourceId
    }

    override fun play(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        Log.d(TAG, "[play] $id")
        player.start()
        return true
    }

    override fun stop(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        Log.d(TAG, "[stop] $id")
        player.reset()
        return true
    }

    override fun pause(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        player.pause()
        return true
    }

    override fun resume(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        player.start()
        return true
    }

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean {
        // TODO : Impl
        return false
    }

    override fun getOffset(id: SourceId): Long {
        return MEDIA_PLAYER_INVALID_OFFSET
    }

    override fun getDuration(id: SourceId): Long {
        return MEDIA_PLAYER_INVALID_OFFSET
    }

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        this.playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        this.bufferEventListener = listener
    }
}