package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Serde

interface ISerializer<T : Any> {
    fun serialize(data: T): String
    fun deserialize(data: String): T
}

class Serializer<T : Any>(private val serializer: ISerializer<T>) : Serde<T> {
    override fun serializer() = SerdeSerializer<T>(serializer)

    override fun deserializer() = SerdeDeserializer<T>(serializer)
}