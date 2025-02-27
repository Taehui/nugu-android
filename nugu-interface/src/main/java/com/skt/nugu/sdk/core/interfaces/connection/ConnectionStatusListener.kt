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
package com.skt.nugu.sdk.core.interfaces.connection

/**
 * This class allows a client to be notified of changes to connection status
 */
interface ConnectionStatusListener {
    /**
     * This enum expresses the connection state.
     */
    enum class Status {
        /** not connected to server*/
        DISCONNECTED,
        /** attempting to establish a connection to server*/
        PENDING,
        /**  connected to server.*/
        CONNECTED
    }

    /**
     * This enum expresses the reasons a connection status may change.
     */
    enum class ChangedReason {
        /**
         * The non-reason, to be used when no reason is specified
         */
        NONE,

        /** The status changed to due to a successful operation.*/
        SUCCESS,

        /** The status changed due to an error from which there is no recovery.*/
        UNRECOVERABLE_ERROR,

        /** The connection status changed due to the client interacting with the Connection public api.*/
        CLIENT_REQUEST,

        /** The connection attempt failed due to the Connection object being disabled.*/
        DISABLED,

        /** The connection attempt failed due to DNS resolution timeout.*/
        DNS_TIMEDOUT,

        /** The connection attempt failed due to timeout.*/
        CONNECTION_TIMEDOUT,

        /** The connection attempt failed due to excessive load on the server.*/
        CONNECTION_THROTTLED,

        /** The access credentials provided to server were invalid.*/
        INVALID_AUTH,

        /** There was a timeout sending a ping request.*/
        PING_TIMEDOUT,

        /** There was a timeout writing to server.*/
        WRITE_TIMEDOUT,

        /** There was a timeout reading from server.*/
        READ_TIMEDOUT,

        /** There was an underlying protocol error.*/
        FAILURE_PROTOCOL_ERROR,

        /** There was an internal error within server.*/
        INTERNAL_ERROR,

        /** There was an internal error on the server.*/
        SERVER_INTERNAL_ERROR,

        /** The server asked the client to reconnect.*/
        SERVER_SIDE_DISCONNECT,

        /** The server endpoint has changed.*/
        SERVER_ENDPOINT_CHANGED
    }

    /**
     * This function will be called when the connection status changes.
     * @param status The current status of the connection.
     * @param reason Reason for changing status
     */
    fun onConnectionStatusChanged(status: Status, reason: ChangedReason)
}