package com.safemode.llconnect.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * LubeLogger returns numeric fields as JSON numbers when the `culture-invariant`
 * header is present, but as quoted strings otherwise. These qualifiers let the
 * models parse either representation so the client is robust to both modes.
 */
@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class FlexDouble

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class FlexLong

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class FlexString

object FlexibleNumberAdapters {

    @FromJson
    @FlexDouble
    fun doubleFromJson(reader: JsonReader): Double? = when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.STRING -> reader.nextString().trim().toDoubleOrNull()
        JsonReader.Token.NUMBER -> reader.nextDouble()
        else -> {
            reader.skipValue()
            null
        }
    }

    @ToJson
    fun doubleToJson(writer: JsonWriter, @FlexDouble value: Double?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }

    @FromJson
    @FlexLong
    fun longFromJson(reader: JsonReader): Long? = when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.STRING -> reader.nextString().trim().substringBefore(".").toLongOrNull()
        JsonReader.Token.NUMBER -> reader.nextLong()
        else -> {
            reader.skipValue()
            null
        }
    }

    @ToJson
    fun longToJson(writer: JsonWriter, @FlexLong value: Long?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }

    @FromJson
    @FlexString
    fun stringFromJson(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.STRING -> reader.nextString()
        JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
        else -> {
            reader.skipValue()
            null
        }
    }

    @ToJson
    fun stringToJson(writer: JsonWriter, @FlexString value: String?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
