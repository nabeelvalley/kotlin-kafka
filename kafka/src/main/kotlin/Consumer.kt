import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.*

typealias Poll<T> = () -> List<T>
typealias Consume<T> = suspend (poll: Poll<T>) -> Unit

fun createConsumer(properties: Properties) =
    KafkaConsumer<String, String>(properties)

fun <T : Any> consume(
    properties: Properties,
    serializer: ISerializer<T>,
    patterns: List<String>,
    duration: Long,
    callback: Consume<T>
) {
    createConsumer(properties).use { consumer ->
        consumer.subscribe(patterns)
        val poll = fun(): List<T> {
            val records = consumer.poll(Duration.ofMillis(duration))
            val data = records.toList()
                .map(ConsumerRecord<String, String>::value)
                .map(serializer::deserialize)

            return data
        }

        runBlocking {
            callback(poll)
        }
    }
}

interface IConsumer<T> {
    /**
     * Returns the raw consumer used by Kafka
     */
    fun createConsumer(): KafkaConsumer<String, String>
    fun consume(topics: List<String>, duration: Long, callback: Consume<T>)
}

class Consumer<T : Any>(
    properties: Properties,
    private val serializer: ISerializer<T>
) : Config(properties), IConsumer<T> {
    override fun createConsumer() = createConsumer(properties)

    override fun consume(topics: List<String>, duration: Long, callback: Consume<T>) =
        consume(properties, serializer, topics, duration, callback)
}