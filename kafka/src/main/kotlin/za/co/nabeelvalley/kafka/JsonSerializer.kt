package za.co.nabeelvalley.kafka

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class JsonSerializer<T : Any>(type: KClass<T>) : ISerializer<T> {
    private val serializer: KSerializer<T> = serializer(type.java) as KSerializer<T>

    override fun serialize(data: T): String = Json.encodeToString(serializer, data)

    override fun deserialize(data: String): T = Json.decodeFromString(serializer, data)
}
