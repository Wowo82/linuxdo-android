package org.linuxdo.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Discourse 的 tags 字段在三种端点里形态不同：
 *
 *   latest.json   → [{"id":543,"name":"ai-plugin","slug":"ai-plugin"}]
 *   search.json   → [{"id":543,"name":"ai-plugin",...}]
 *   话题详情      → ["ai-plugin"]
 *
 * 这个序列化器两种都吃，反序列化时统一成 TagOrString。
 */
object TagOrStringSerializer : KSerializer<TagOrString> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("org.linuxdo.android.data.model.TagOrString")

    override fun deserialize(decoder: Decoder): TagOrString {
        val input = decoder as? JsonDecoder
            ?: return TagOrString(decoder.decodeString())

        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> TagOrString(element.content)

            is JsonObject -> {
                val obj: JsonObject = element.jsonObject
                TagOrString(
                    name = obj["name"]?.jsonPrimitive?.content
                        ?: obj["slug"]?.jsonPrimitive?.content
                        ?: "",
                    id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull(),
                )
            }

            else -> TagOrString("")
        }
    }

    override fun serialize(encoder: Encoder, value: TagOrString) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder == null) {
            encoder.encodeString(value.name)
            return
        }
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("name", JsonPrimitive(value.name))
                value.id?.let { put("id", JsonPrimitive(it)) }
            }
        )
    }
}
