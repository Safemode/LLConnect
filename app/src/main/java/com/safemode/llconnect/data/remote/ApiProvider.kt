package com.safemode.llconnect.data.remote

import com.safemode.llconnect.data.settings.AuthMode
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds and caches a [LubeLoggerApi] bound to the current [ConnectionConfig].
 * Retrofit requires an absolute base URL, so the instance is rebuilt whenever the
 * host/port/scheme change. Auth + culture-invariant headers are injected per request
 * from the latest config, so credential-only changes don't require a rebuild.
 */
class ApiProvider {

    @Volatile
    private var config: ConnectionConfig = ConnectionConfig()

    @Volatile
    private var cachedApi: LubeLoggerApi? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    private val moshi: Moshi = Moshi.Builder()
        .add(FlexibleNumberAdapters)
        .add(KotlinJsonAdapterFactory())
        .build()

    fun updateConfig(newConfig: ConnectionConfig) {
        config = newConfig
    }

    /** Latest known connection config (for components like the image loader). */
    fun currentConfig(): ConnectionConfig = config

    /**
     * An OkHttp client that injects only the auth headers (no JSON Accept / culture headers),
     * so protected vehicle images can be fetched by Coil.
     */
    fun authenticatedImageClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val current = config
            val builder = chain.request().newBuilder()
            when (current.authMode) {
                AuthMode.API_KEY -> if (current.apiKey.isNotBlank()) {
                    builder.header("x-api-key", current.apiKey)
                }
                AuthMode.BASIC -> if (current.basicUsername.isNotBlank()) {
                    builder.header(
                        "Authorization",
                        Credentials.basic(current.basicUsername, current.basicPassword),
                    )
                }
            }
            chain.proceed(builder.build())
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Current API, or null when the host isn't set yet. */
    fun apiOrNull(): LubeLoggerApi? {
        val current = config
        if (current.host.isBlank()) return null
        val baseUrl = current.baseUrl()
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == baseUrl) return existing

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val api = retrofit.create(LubeLoggerApi::class.java)
        cachedApi = api
        cachedBaseUrl = baseUrl
        return api
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("x-api-key")
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { config })
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Adds authentication and culture-invariant headers using the latest config. */
class AuthInterceptor(private val configProvider: () -> ConnectionConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = configProvider()
        val builder = chain.request().newBuilder()
        when (config.authMode) {
            AuthMode.API_KEY -> if (config.apiKey.isNotBlank()) {
                builder.header("x-api-key", config.apiKey)
            }
            AuthMode.BASIC -> if (config.basicUsername.isNotBlank()) {
                builder.header(
                    "Authorization",
                    Credentials.basic(config.basicUsername, config.basicPassword),
                )
            }
        }
        if (config.cultureInvariant) {
            builder.header("culture-invariant", "true")
        }
        builder.header("Accept", "application/json")
        return chain.proceed(builder.build())
    }
}
