package tracker.app

import IConsumer
import IProducer
import IStreamBuilder
import KafkaClient
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
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
    val streamJob = stream(client.streamInverse, inputTopic, outputTopic)


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

fun stream(streamBuilder: IStreamBuilder<GeneratedData, ProcessedData>, inputTopic: String, outputTopic: String): Job {
    val scope = CoroutineScope(Dispatchers.IO)
    val stream = streamBuilder.fromTopic(inputTopic)

    val processor = stream.getProcessor {
        it.mapValues { key, value ->
            ProcessedData(value, Date().toString())
        }
    }

    return scope.launch {
        stream.startStreaming(outputTopic, processor) { close ->
            scope.launch {

                // Non-blocking loop as long as the coroutine is active
                while (isActive) {
                    delay(10_000)
                }

                // close when no longer active
                close()
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
