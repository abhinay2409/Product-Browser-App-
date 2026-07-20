package com.adrinova.productbrowser.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Created by Abhinay on 20/07/26.
 */

/**
 * Single place that configures Ktor. The platform engine (OkHttp on
 * Android, Darwin on iOS) is discovered automatically from the source-set
 * dependencies; tests inject a MockEngine through the overload below.
 */
object HttpClientFactory {

    fun create(): HttpClient = HttpClient { configure() }

    fun create(engine: HttpClientEngine): HttpClient = HttpClient { configure() }

    private fun HttpClientConfig<*>.configure() {
        expectSuccess = true // non-2xx responses throw ResponseException

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
}