package example

import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import za.co.nabeelvalley.kafka.*
import java.io.FileInputStream
import java.util.*

fun main() {
    val inputTopic = "input"
    val outputTopic = "output"
    val properties = loadProperties()

    // input processed, output generated
    val client = KafkaClient(properties, ProcessedData::class, GeneratedData::class)

    client.admin.createTopic(inputTopic)
    client.admin.createTopic(outputTopic)

    val consumeJob = consume(client.consumer, outputTopic)
    val produceJob = produce(client.producer, inputTopic)
    val streamJob = process(client.streamInverse, inputTopic, outputTopic)


    runBlocking {
        produceJob.join()
        consumeJob.join()
        streamJob.join()
    }
}

@Serializable
data class GeneratedData(val message: String, val key: Int)

@Serializable
data class ProcessedData(val input: GeneratedData, val processed: String)

fun loadProperties(): Properties {
    val props = Properties()
    val resource = ClassLoader.getSystemResource("application.properties")
    println("File  path: ${resource.path}")
    FileInputStream(resource.path).use { stream ->
        props.load(stream)
    }

    val dotenv = Dotenv.load()
    props["bootstrap.servers"] = dotenv["BOOTSTRAP_SERVERS"]
    props["sasl.jaas.config"] = dotenv["SASL_JAAS_CONFIG"]

    return props
}

@Serializable
data class ProducerData(val message: String, val key: Int)

fun instantiateAndProduce(properties: Properties): Unit {
    val serializer = JsonSerializer(ProducerData::class)
    val producer = Producer(properties, serializer)

    runBlocking {
        producer.produce { send ->
            val data = ProducerData("Hello world", 1)
            send("my-topic", data)
        }
    }
}


@Serializable
data class ConsumerData(val message: String, val key: Int)

fun instantiateAndConsume(properties: Properties): Unit {
    val serializer = JsonSerializer(ConsumerData::class)
    val consumer = Consumer(properties, serializer)

    runBlocking {
        consumer.consume(listOf("my-topic"), 1000) { poll ->
            while (true) {
                val messages = poll()

                println("Received ${messages.size} messages")
                messages.forEach(fun(message) {
                    println("Received: $message")
                })
            }
        }
    }
}


fun consume(client: IConsumer<ProcessedData>, topic: String): Job {
    val consumerScope = CoroutineScope(Dispatchers.IO)
    return consumerScope.launch {
        println("Consumer Starting")
        client.consume(listOf(topic), 1000) { poll ->
            while (true) {
                val messages = poll()

                println("Received ${messages.size} messages")
                messages.forEach(fun(message) {
                    println("Received: $message")
                })
            }
        }
    }
}

fun process(streamBuilder: IStreamBuilder<GeneratedData, ProcessedData>, inputTopic: String, outputTopic: String): Job {
    val scope = CoroutineScope(Dispatchers.IO)
    val stream = streamBuilder.fromTopic(inputTopic)

    val processor = stream.getProcessor {
        it.mapValues { key, value ->
            ProcessedData(value, Date().toString())
        }
    }

    return scope.launch {
        stream.startStreaming(outputTopic, processor) { close ->
            coroutineScope {
                println("Processor starting")
                // Non-blocking loop as long as the coroutine is active
                while (isActive) {
                    delay(10_000)
                }

                // close when no longer active
                close()
                println("Processor closed")
            }
        }
    }
}

fun initializeAndProcess(properties: Properties): Job {
    val producedSerializer = JsonSerializer(ProducerData::class)
    val consumedSerializer = JsonSerializer(ConsumerData::class)
    val streamBuilder = StreamBuilder(properties, consumedSerializer, producedSerializer)
    val stream = streamBuilder.fromTopic("input-topic")

    val processor = stream.getProcessor { kStream ->
        kStream.mapValues { key, value ->
            ProducerData("Message processed: $key", value.key)
        }
    }

    val scope = CoroutineScope(Dispatchers.IO)
    return scope.launch {
        stream.startStreaming("output-topic", processor) { close ->
            coroutineScope {
                println("Processor starting")
                // Non-blocking loop as long as the coroutine is active
                while (isActive) {
                    delay(10_000)
                }

                // close when no longer active
                close()
                println("Processor closed")
            }
        }
    }
}


fun produce(client: IProducer<GeneratedData>, topic: String): Job {
    val producerScope = CoroutineScope(Dispatchers.IO)
    return producerScope.launch {
        println("Producer Starting")
        client.produce { send ->
            var count = 1
            while (true) {
                count++
                delay(5000)
                val message = GeneratedData("Hello World", count)
                send(topic, message)
                println("Sent: $message")
            }
        }
    }
}
