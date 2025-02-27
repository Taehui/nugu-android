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
package com.skt.nugu.sdk.core.interfaces.auth

/**
 * Interface for authorization
 */
interface AuthDelegate {
    /**
     * addAuthStateListener adds an [AuthStateListener] on the given was changed
     * @param listener the listener that added
     */
    fun addAuthStateListener(listener: AuthStateListener)
    /**
     * Removes an [AuthStateListener]
     * @param listener the listener that removed
     */
    fun removeAuthStateListener(listener: AuthStateListener)
    /**
     * Gets an authorization from cache
     * @return authorization ( auth_type + access_token )
    */
    fun getAuthorization(): String?
    /**
     * Receive authentication failure events
     * @param token Failed token means
     */
    fun onAuthFailure(token: String?)
}