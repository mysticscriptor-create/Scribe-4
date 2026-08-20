package com.primaloptima.scribe.engine

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class SerializedDocument(
    val version: Int = 2,
    val plainText: String,
    val spans: List<SerializedSpan> = emptyList()
) {
    fun toJson(): String = AppJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): SerializedDocument? = try {
            AppJson.decodeFromString<SerializedDocument>(json)
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
data class SerializedSpan(
    val type: String,   // FormatType.name
    val start: Int,
    val end: Int
)

fun Note.toSerializedDocument(): SerializedDocument {
    if (!formatsJson.isNullOrBlank()) {
        SerializedDocument.fromJson(formatsJson)?.let { return it }
    }
    return SerializedDocument(
        version = 2,
        plainText = content,
        spans = emptyList()
    )
}
