# Kafka Library for Kotlin

A small library for working with Kafka from Kotlin with built-in support for:

1. JSON Serialization and Deserialization
2. Coroutines for managing stream lifecycles
3. Simple clients for common Kafka related operations
   1. Producing records
   2. Consuming records
   3. Stream processing

# References

- [Introduction to Kafka](https://kafka.apache.org/intro)
- [Java Kafka Client](https://docs.confluent.io/kafka-clients/java/current/overview.html)

# Overview

The purpose of this post is to illustrate a method of interacting with Kafka using Kotlin in a functional programming style while using Kotlin coroutines for a multi-threading means of interacting with Kafka. We will be interacting with the [Kafka Client for Java](https://docs.confluent.io/kafka-clients/java/current/overview.html) and will be building a small library on top of this for the purpose of simplifying communication and handling tasks like JSON Serialization

# Using the Producer

In our application code we can instantiate and use the producer as follows:

Firstly, we need to define the type of data we are goind to send with the `@Serializable` annotation

`App.kt`

```kotlin
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import za.co.nabeelvalley.kafka.*
import java.util.*


@Serializable
data class ProducerData(val message: String, val key: Int)
```

Next, we can define a function for producing data, this will require the `properties` we loaded previously:

`App.kt`

```kotlin
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
```

We use `runBlocking` since our producer needs a coroutine scope in which to send data. Sending data us used within the `produce` method in which we create some data and call the `send` method provide by the `produce` function

An interesting to note is that we are passing the `class` of our data to the serializer to create an instance - this is the usage of the funky reflection thing we saw previously

# Using the Consumer

Using the `Consumer` follows a very similar pattern to the producer, however we need to create a loop that will poll for data and handle as necessary when data is received:

`App.kt`

```kotlin
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
```

In the above, we use a `while(true)` loop to re-poll continuously but this can freely change on the implementation, similar to with the producer code

# Using the Stream Processor

We can use the stream processor code:

`App.kt`

```kotlin
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
```

Most of this is just the normal construction that you will have for any instance of the stream client, what is interesting is the part where we define the processor:

`App.kt`

```kotlin
val processor = stream.getProcessor { kStream ->
    kStream.mapValues { key, value ->
        ProducerData("Message processed: $key", value.key)
    }
}
```

In the above example we are simply mapping a single record using `mapValues`, this is very similar to the Collection methods available in Kotlin but is instead used to define how data will be transformed in the stream

The processor we define is what will be executed on records or groups of records depending on how we want to handle the resulting data

# Conclusion

In this post we've covered the basic implementation of how we can interact with Kafka using the Kotlin programming language and built a small library that takes us through the basic use cases of Serializing, Producing, Consuming, and Processing stream data
