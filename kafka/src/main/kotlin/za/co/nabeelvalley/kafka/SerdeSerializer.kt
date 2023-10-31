package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Serializer

class SerdeSerializer<T : Any>(private val serializer: ISerializer<T>) : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        val result = serializer.serialize(data)
        return result.toByteArray()
    }
}