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
package com.skt.nugu.sdk.platform.android.login.net

import java.io.*
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.*
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Provide a base class for http client
 */
class HttpClient {

    private class DefaultTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate?> {
            return arrayOfNulls(0)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }
    }

    /**
     * Returns a [HttpsURLConnection] instance
     */
    private fun getConnection(uri: String) : HttpsURLConnection{
        val connection = URL(uri).openConnection() as HttpsURLConnection
        connection.hostnameVerifier = HostnameVerifier { hostname, session -> true }
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty( "charset", "utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.useCaches = false
        connection.readTimeout = 10 * 1000
        connection.connectTimeout = 10 * 1000
        connection.doOutput = true
        connection.doInput = true
        return connection
    }

    /**
     * Prepare the request, Invokes the request immediately
     */
    fun newCall(uri: String, form: FormEncodingBuilder): Response {
        // configure the SSLContext with a TrustManager
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(arrayOfNulls(0), arrayOf(DefaultTrustManager()), SecureRandom())
        SSLContext.setDefault(ctx)

        val connection = getConnection(uri)
        try {
            connection.connect()

            DataOutputStream(connection.outputStream).apply {
                writeBytes(form.toString())
                flush()
                close()
            }

            return when(connection.responseCode) {
                HttpsURLConnection.HTTP_OK -> {
                    val stream = BufferedInputStream(connection.inputStream)
                    Response(connection.responseCode, readStream(inputStream = stream))
                }
                else -> {
                    try {
                        val stream = BufferedInputStream(connection.inputStream)
                        Response(connection.responseCode, readStream(inputStream = stream))
                    } catch (e : FileNotFoundException) {
                        Response(connection.responseCode, "")
                    }
                }
            }
        } catch (e : UnknownHostException) {
            return Response(499, "UnknownHostException")
        } catch (e : SSLException) {
            return Response(499, "SSLException")
        } catch (e: IOException) {
            return Response(499, "IOException")
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(inputStream: BufferedInputStream): String {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        bufferedReader.forEachLine { stringBuilder.append(it) }
        return stringBuilder.toString()
    }
}
