package za.co.nabeelvalley.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import java.util.*

typealias TransformProcessor<TConsume, TProduce> = (stream: KStream<String, TConsume>) -> KStream<String, TProduce>
typealias Close = () -> Unit

class SerializedStream<TConsume : Any, TProduce : Any>(
    private val properties: Properties,
    private val builder: StreamsBuilder,
    private val producer: Produced<String, TProduce>,
    private val stream: KStream<String, TConsume>
) {
    fun startStreaming(
        topic: String,
        processor: KStream<String, TProduce>,
        process: suspend (close: Close) -> Unit
    ): Job {
        processor.to(topic, producer)

        val streams = KafkaStreams(
            builder.build(),
            properties
        )

        val scope = CoroutineScope(Dispatchers.IO)
        return scope.launch {
            streams.start()
            process(streams::close)
        }
    }

    fun getProcessor(
        processor: TransformProcessor<TConsume, TProduce>
    ): KStream<String, TProduce> = processor(stream)
}