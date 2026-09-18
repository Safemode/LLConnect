package com.safemode.llconnect.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Converts a request model into `application/x-www-form-urlencoded` fields.
 *
 * LubeLogger's server (`QueryParamFilter`) crashes on JSON request bodies — it tries to
 * rewind the non-seekable request stream and throws, taking the container down. Its web UI
 * uses form-encoded posts, which ASP.NET buffers (seekable), so the filter is happy. We do
 * the same: send writes as form fields.
 *
 * Nested objects/arrays are flattened with ASP.NET model-binding names, e.g.
 * `files[0].name`, `extraFields[0].value`.
 */
object FormEncoder {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun fields(request: Any): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        @Suppress("UNCHECKED_CAST")
        val adapter = moshi.adapter(request.javaClass) as com.squareup.moshi.JsonAdapter<Any>
        flatten("", adapter.toJsonValue(request), out)
        return out
    }

    private fun flatten(prefix: String, value: Any?, out: MutableMap<String, String>) {
        when (value) {
            null -> {}
            is Map<*, *> -> value.forEach { (key, v) ->
                val name = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                flatten(name, v, out)
            }
            is List<*> -> value.forEachIndexed { index, v -> flatten("$prefix[$index]", v, out) }
            is Boolean -> out[prefix] = value.toString()
            is Double -> out[prefix] =
                if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            else -> out[prefix] = value.toString()
        }
    }
}
