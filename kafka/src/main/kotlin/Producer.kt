import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*
import kotlinx.coroutines.runBlocking

fun createProducer(properties: Properties) =
    KafkaProducer<String, String>(properties)

fun createRecord(topic: String, message: String) =
    ProducerRecord<String, String>(topic, message)

typealias Send<T> = (topic: String, message: T) -> Unit
typealias Produce<T> = suspend (send: Send<T>) -> Unit

fun <T : Any> produce(properties: Properties, serializer: ISerializer<T>, callback: Produce<T>) {
    createProducer(properties).use {
        val send = fun(topic: String, message: T) {
            val payload = serializer.serialize(message)
            val record = createRecord(topic, payload)
            it.send(record)
        }

        runBlocking {
            callback(send)
        }
    }
}


interface IProducer<T> {
    /**
     * Returns the raw producer used by Kafka
     */
    fun createProducer(): KafkaProducer<String, String>
    fun produce(callback: Produce<T>)
}


class Producer<T : Any>(
    properties: Properties,
    private val serializer: ISerializer<T>
) : Config(properties),
    IProducer<T> {
    override fun createProducer() = createProducer(properties)

    override fun produce(callback: Produce<T>) = produce<T>(properties, serializer, callback)
}