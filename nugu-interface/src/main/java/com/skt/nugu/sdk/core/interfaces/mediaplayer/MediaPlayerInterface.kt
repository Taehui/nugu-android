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
package com.skt.nugu.sdk.core.interfaces.mediaplayer

import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import java.net.URI

const val MEDIA_PLAYER_INVALID_OFFSET = -1L

/**
 * Interface of a media player
 *
 */
interface MediaPlayerControlInterface {
    fun play(id: SourceId): Boolean
    fun stop(id: SourceId): Boolean
    fun pause(id: SourceId): Boolean
    fun resume(id: SourceId): Boolean
    fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean
    fun getOffset(id: SourceId): Long
    fun getDuration(id: SourceId): Long
    fun setPlaybackEventListener(listener: PlaybackEventListener)
    fun setBufferEventListener(listener: BufferEventListener)

    interface PlaybackEventListener {
        fun onPlaybackStarted(id: SourceId)
        fun onPlaybackFinished(id: SourceId)
        fun onPlaybackError(id: SourceId, type: ErrorType, error: String)
        fun onPlaybackPaused(id: SourceId)
        fun onPlaybackResumed(id: SourceId)
        fun onPlaybackStopped(id: SourceId)
    }

    interface BufferEventListener {
        fun onBufferUnderrun(id: SourceId)
        fun onBufferRefilled(id: SourceId)
    }
}

interface AttachmentSourcePlayable {
    fun setSource(attachmentReader: Attachment.Reader): SourceId
}

interface UriSourcePlayable {
    fun setSource(uri: URI): SourceId
}

interface AttachmentPlayablePlayer : MediaPlayerControlInterface, AttachmentSourcePlayable
interface UriSourcePlayablePlayer: MediaPlayerControlInterface, UriSourcePlayable
interface MediaPlayerInterface: MediaPlayerControlInterface, AttachmentSourcePlayable, UriSourcePlayable