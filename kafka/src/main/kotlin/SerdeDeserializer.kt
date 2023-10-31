import org.apache.kafka.common.serialization.Deserializer

class SerdeDeserializer<T : Any>(private val serializer: ISerializer<T>) : Deserializer<T?> {
    override fun deserialize(topic: String?, data: ByteArray?): T {
        try {
            val string = String(data!!)
            return serializer.deserialize(string)
        } catch (error: Error) {
            println("Error Deserializing Data: $error")
            throw error
        }
    }
}