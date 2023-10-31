package za.co.nabeelvalley.kafka

import java.util.*
import kotlin.reflect.KClass

class KafkaClient<TConsume : Any, TProduce : Any>(
    properties: Properties,
    consumeType: KClass<TConsume>,
    produceType: KClass<TProduce>,
    produceSerializer: ISerializer<TProduce> = JsonSerializer(produceType),
    consumeSerializer: ISerializer<TConsume> = JsonSerializer(consumeType),
) {
    val consumer: IConsumer<TConsume> = Consumer(properties, consumeSerializer)

    val producer: IProducer<TProduce> = Producer(properties, produceSerializer)

    val stream: IStreamBuilder<TConsume, TProduce> =
        StreamBuilder(properties, consumeSerializer, produceSerializer)

    val streamInverse: IStreamBuilder<TProduce, TConsume> =
        StreamBuilder(properties, produceSerializer, consumeSerializer)

    val admin: IAdmin = Admin(properties)
}