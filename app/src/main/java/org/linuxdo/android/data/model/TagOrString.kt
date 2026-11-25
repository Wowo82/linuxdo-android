package org.linuxdo.android.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Discourse 的 tags 字段不一致：
 *   latest.json   → [{"id":543,"name":"ai-plugin","slug":"ai-plugin"}]
 *   search.json   → [{"id":543,"name":"ai-plugin",...}]
 *   话题详情      → ["ai-plugin"] 或 ["tag"]
 * 这个序列化器两种都吃。
 */
object TagOrStringSerializer : KSerializer<TagOrString> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("TagOrString")

    override fun deserialize(decoder: Decoder): TagOrString {
        val input = decoder as? JsonDecoder
            ?: return TagOrString(decoder.decodeString())
        return when (val el = input.decodeJsonElement()) {
            is JsonPrimitive -> TagOrString(el.content)
            is JsonObject -> {
                val obj = el.jsonObject
                TagOrString(
                    name = obj["name"]?.jsonPrimitive?.content
                        ?: obj["slug"]?.jsonPrimitive?.content.orEmpty(),
                    id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull(),
                )
            }
            else -> TagOrString("")
        }
    }

    override fun serialize(encoder: Encoder, value: TagOrString) {
        val out = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: run { encoder.encodeString(value.name); return }
        out.encodeJsonElement(buildJsonObject {
            put("name", JsonPrimitive(value.name))
            value.id?.let { put("id", JsonPrimitive(it)) }
        })
    }
}
