package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import java.util.*

interface IStreamBuilder<TConsume : Any, TProduce : Any> {
    fun fromTopic(topic: String): SerializedStream<TConsume, TProduce>
    fun fromTopics(topics: List<String>): SerializedStream<TConsume, TProduce>
}

class StreamBuilder<TConsume : Any, TProduce : Any>(
    properties: Properties,
    consumeSerializer: ISerializer<TConsume>,
    producerSerializer: ISerializer<TProduce>,
) : Config(properties), IStreamBuilder<TConsume, TProduce> {
    private val inputSerde = Serializer<TConsume>(consumeSerializer)
    private val consumed = Consumed.with(Serdes.String(), inputSerde)

    private val outputSerde = Serializer<TProduce>(producerSerializer)
    private val produced = Produced.with(Serdes.String(), outputSerde)

    override fun fromTopic(topic: String): SerializedStream<TConsume, TProduce> {
        val builder = StreamsBuilder()
        val stream = builder.stream(mutableListOf(topic), consumed)

        return SerializedStream(properties, builder, produced, stream)
    }

    override fun fromTopics(topics: List<String>): SerializedStream<TConsume, TProduce> {
        val builder = StreamsBuilder()
        val stream = builder.stream(topics.toMutableList(), consumed)

        return SerializedStream(properties, builder, produced, stream)
    }
}