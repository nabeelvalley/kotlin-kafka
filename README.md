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

> If you would like to view the completed source code, you can take a look at the [kotlin-kafka GitHub repository](https://github.com/nabeelvalley/kotlin-kafka)

## Kafka

According to then [Kafka Website](https://kafka.apache.org/):

> "Apache Kafka is an open-source distributed event streaming platform:

Generally we can think of Kafka as a platform that enables us to connect data producers to data.

Kafka is an event platform that provides us with a few core functions:

1. Publishing and subscribing event data
2. Processing of events in real-time or retrospectively
3. Storage of event streams

From a more detailed perspective, Kafka internally handles storage of event streams, but we are given control over the means of data production, consumption, and processing via the Kafka API, namely:

- The Producer API for production
- The Consumer API for subscription
- The Streams API for processing stream data

In addition to the above, we we will also touch on the **Admin API** that enables us to do some basic management tasks of our Kafka instance

## Kotlin

Kotlin is a statically typed programming language built on the Java Virtual Machine that provides interop with Java code

# The Code

## Config

To get some admin stuff out of the way, before you can really do any of this you will to have a `.env` file in the project that you can load which contains some application configuration, for the purpose of our application we require the following config in this file - below is some example content

`.env`

```sh
BOOTSTRAP_SERVERS=my-server-url:9092
SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="someUsername" password="somePassword";
```

Additionally, we have some non-sensitive config in our `application.properties` file in our application `resources` folder which contains the following:

`resources/application.properties`

```properties
sasl.mechanism=SCRAM-SHA-256
security.protocol=SASL_SSL
key.serializer=org.apache.kafka.common.serialization.StringSerializer
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.serializer=org.apache.kafka.common.serialization.StringSerializer
value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
auto.offset.reset=earliest
group.id=$GROUP_NAME
application.id=example-app
```

Next, we need to load this in our application to create a `Properties` object along with all the other application config we require. We can create the Properties object using the `application.properties` and `.env` files as follows:

`App.kt`

```kotlin
package example

import io.github.cdimascio.dotenv.Dotenv
import java.io.FileInputStream
import java.util.*

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
```

The above example uses the `io.github.cdimascio:dotenv-java:3.0.0` package for loading the environment variables and some builtin Java utilities for loading the application properties file

Next, for the purpose of using it with our library we will create a `Config` class that wraps the properties file we defined so that we can use this a little more elegantly in our consumers. Realistically we probably should do some validation on the resulting Properties that we load in but we'll just keep it simple and define `Config` as a class that contains the properties as a property:

`Config.kt`

```kotlin
package za.co.nabeelvalley.kafka

import java.util.Properties

open class Config(internal val properties: Properties) {}
```

## Working with JSON Data

An important part of what we want our client to handle is the JSON serialization and deserialization when sending data to Kafka. Sending JSON data is not a requirement of Kafka as a platform, but it's the usecase that we're building our library around and so is something we need to consider

### Serialization

Serialization in this context refers to the process of converting our Kotlin classes into a string and back to a Kotlin class. For this discussion we will refer to a class that is able to do this bidirectional conversion as a Serializer.

We can define generic representation of a serializer as a class that contains a method callsed `serialize` that takes in data of type `T` and returns a string, and contains a method called `deserialize` that takes in a string and returns an object of type `T`

> Not that at this point we're not considering that the serializer needs to return JSON. In our context a JSON serializer is just a specific implementation of the serialization concept that we have defined

An interface that describes the `Serializer` we mentioned above can be seen as follows:

`Serializer.kt`

```kotlin
package za.co.nabeelvalley.kafka

interface ISerializer<T : Any> {
    fun serialize(data: T): String
    fun deserialize(data: String): T
}
```

### JSON Serialization

Given the definition of a serializer we can define a JSON serializer that uses the `kotlinx.serialization` library and implements our `ISerializer` as follows:

`JsonSerializer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class JsonSerializer<T : Any>(type: KClass<T>) : ISerializer<T> {
    private val serializer: KSerializer<T> = serializer(type.java) as KSerializer<T>

    override fun serialize(data: T): String = Json.encodeToString(serializer, data)

    override fun deserialize(data: String): T = Json.decodeFromString(serializer, data)
}
```

The above code is a little funky since we're using reflection on the actual class of the input data to define our serializer, other than we're just using the `kotlinx` serializer to handle the data transformation. The thing that matters in this context is that we are able abstract the reflection aspect of the serializer, this will help make the final interface we provide to the user for working with Kafka simpler

### Serde Serializer

Now that we have defined a simple representation of a serializer that provides some interop with the Kotlin data types, we need to implement the other side of this which is a `SerdeSerializer` which is what the Kafka Clients need to work with. The requirements of this serializer are a little different to the one we defined above. This serializer needs to:

1. Have a separate `Serializer` and `Deserializer` interfaces that need to be implemented
2. Return a `ByteArray` instead of `String`

We can define these serializers such that they can be constructed from and `ISerializer` interface that we defined previously. This will make it possible for consumers of our library to swap our their serialization strategy to enable other usecases than the simple JSON communication we are considering

As mentioned above, we need to implement a separate `Serializer` and `Deserializer` respecively as:

`SerdeSerializer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Serializer

class SerdeSerializer<T : Any>(private val serializer: ISerializer<T>) : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        val result = serializer.serialize(data)
        return result.toByteArray()
    }
}
```

And

`SerdeDeserializer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Deserializer

class SerdeDeserializer<T : Any>(private val serializer: ISerializer<T>) : Deserializer<T?> {
    override fun deserialize(topic: String?, data: ByteArray?): T? {
        try {
            val string = String(data!!)
            return serializer.deserialize(string)
        } catch (error: Error) {
            println("Error Deserializing Data: $error")
            return null
        }
    }
}
```

> Our implementation is a little basic and will just ignore any data that we can't serialize, however depending on our usecase we may need to handle this differently

Lastly, we define the actual `Serde` Serializer implementation using the above implementations:

`Serializer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import org.apache.kafka.common.serialization.Serde

class Serializer<T : Any>(private val serializer: ISerializer<T>) : Serde<T> {
    override fun serializer() = SerdeSerializer<T>(serializer)

    override fun deserializer() = SerdeDeserializer<T>(serializer)
}
```

As far as serialization and deserialization goes, this should be everything we need for working with JSON data

## Producing Data

Producing data is a method by which a client sends data to a Kafka topic. We can define this as a type as follows:

`Producer.kt`

```kotlin
typealias Send<T> = (topic: String, message: T) -> Unit
```

Now, to provide a functional library interface we will want to provider application code a space in which they will be able to work with the producer that we populate without needing to create a new producer for each message we want to send

We'll codify this intent as a type as follows:

`Producer.kt`

```kotlin
typealias Produce<T> = suspend (send: Send<T>) -> Unit
```

> Note that we define this as a `suspend` function that will enable users to send messages from within a Corouting context

Next, we define the type of our producer as method with a way to create a prodcuer instance for users who may want to manage the lifecycle of the `KafkaProducer` on their own. This however also means they lose access to the automatic serialization and deserialization that we will provide via our `producer` method

This interface is defined as follows:

`Producer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import java.util.*

interface IProducer<T> {
    /**
     * Returns the raw producer used by Kafka
     */
    fun createProducer(): KafkaProducer<String, String>
    fun produce(callback: Produce<T>)
}
```

For the purpose of our implementation we can define some functions ourside of our class that will provde the implementation we require

For the `createProducer` function, we simply provide a wrapper around the `KafkaProducer` provided to us by the Java Kafka Client Library:

`Producer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*
import kotlinx.coroutines.runBlocking

fun createProducer(properties: Properties) =
    KafkaProducer<String, String>(properties)
```

For the sake of consistency, we will do the same for the concept of a `ProducerRecord` which will be used by the produce function:

`Producer.kt`

```kotlin
fun createRecord(topic: String, message: String) =
    ProducerRecord<String, String>(topic, message)
```

Next, the `produce` function can be defined. The role of this function is to handle serialization of data and provide a means for a user to send data to a topic

The producer will take a callback which is the context in which any usage of the `send` function should be used before the producer is disposed:

`Producer.kt`

```kotlin
fun <T : Any> produce(properties: Properties, serializer: ISerializer<T>, callback: Produce<T>) {
    createProducer(properties).use { producer ->
        val send = fun(topic: String, message: T) {
            val payload = serializer.serialize(message)
            val record = createRecord(topic, payload)
            producer.send(record)
        }

        runBlocking {
            callback(send)
        }
    }
}
```

We have also added the `properties` and `serializer` values as an input to the producer as this is needed by Kafka, lastly, we will define our actual `Producer` implementation which builds on the functions we defined above

Note that our `Producer` class implements `IProducer` and extends `Config`, this is because we use the `Config` class as the source of truth of the configuration to be used for our Kafka instance and we want to able to access this config

`Producer.kt`

```kotlin
class Producer<T : Any>(
    properties: Properties,
    private val serializer: ISerializer<T>
) : Config(properties),
    IProducer<T> {
    override fun createProducer() = createProducer(properties)

    override fun produce(callback: Produce<T>) = produce<T>(properties, serializer, callback)
}
```

At this point we have a complete implementation of a producer

### Using the Producer

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

## Consuming Data

Our code for consuming data will follow a similar pattern to what we use to consume the data in the previous section

For consuming data, Kafka relies on the concept of polling for records from the part of the consumer, for our client, we will expose using the following type which defines a poll as a method that takes nothing and returns a list of data of type `T`

`Consumer.kt`

```kotlin
typealias Poll<T> = () -> List<T>
```

Next, we can define the type that defines how we want our data to be consumed. For our sake, this is a suspend function that will receive a poll method that it can call to get data

`Consumer.kt`

```kotlin
typealias Consume<T> = suspend (poll: Poll<T>) -> Unit
```

Next, as before, we can define an interface for a `Consumer` in which we have a method to create a `KafkaConsumer` and a method for actually consuming the data. In the case of consuming we need a list of topics to read from as well as the polling frequency duration.

`Consumer.kt`

```kotlin
package za.co.nabeelvalley.kafka

import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.*

interface IConsumer<T> {
    /**
     * Returns the raw consumer used by Kafka
     */
    fun createConsumer(): KafkaConsumer<String, String>
    fun consume(topics: List<String>, duration: Long, callback: Consume<T>)
}
```

Next, we can define our `createConsumer` method quite simply as:

`Consumer.kt`

```kotlin
fun createConsumer(properties: Properties) =
    KafkaConsumer<String, String>(properties)
```

And we can define our `consume` method such that it takes in the `properties` and `serializer` as with the producer, but will also take som `patterns` to be used for subscribing to and the `duration` above, and finally the callback `Consume` function:

`Consumer.kt`

```kotlin
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
```

The `consume` function is very similar to the `produce` function we defined previously, however now instead of being provided a function to send data we now have a function that will return that data

Lastly, we can finish off the definition of our `Consumer` using what we have above:

`Consumer.kt`

```kotlin
class Consumer<T : Any>(
    properties: Properties,
    private val serializer: ISerializer<T>
) : Config(properties), IConsumer<T> {
    override fun createConsumer() = createConsumer(properties)

    override fun consume(topics: List<String>, duration: Long, callback: Consume<T>) =
        consume(properties, serializer, topics, duration, callback)
}
```

## Using the Consumer

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

## Stream Processing

In Kafka, we can think of a stream process as a combination of a consumer and producer such that data comes in from a topic and is sent to a different topic

The thing that makes streams interesting is the builder API that the Kafka Java Library provides to us for defining the operations to be done on the stream data. For our implementation we'll be referring to this as a `TransformProcessor`, this processor needs to take in some data of type `TConsume` and return data of type `TProduce`, however, since we want to provide users complete flexibility in working with this data, we will instead more generally allow a user to convert a stream between the predefined data types, using the underlying library this is called a `KStream`

From a type perspective, we can define a `TransformProcessor` as follows:

`SerializedStream.kt`

```kotlin
typealias TransformProcessor<TConsume, TProduce> = (stream: KStream<String, TConsume>) -> KStream<String, TProduce>
```

Now, we're going to be starting this implementation from what we want, assuming that it is possible for us to in some way define a `KStream` that is instantiated to work with our connection and the respective `TConsume` and `TProduce` data.

We will also be using a type called `Produced` which is what the Kafka Client uses to represent the data that the stream will return since this is what we need in order to send data to a processor

Our implementation will be called a `SerializedStream` and this looks like the following:

`SerializedStream.kt`

```kotlin
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
typealias Process = suspend (close: Close) -> Unit

class SerializedStream<TConsume : Any, TProduce : Any>(
    private val properties: Properties,
    private val builder: StreamsBuilder,
    private val producer: Produced<String, TProduce>,
    private val stream: KStream<String, TConsume>
) {
   fun startStreaming(
      topic: String,
      processor: KStream<String, TProduce>,
      process: Process
   ): Job {
      processor.to(topic, producer)

      val streams = KafkaStreams(
         builder.build(),
         properties
      )

      val scope = CoroutineScope(Dispatchers.IO)
      return scope.launch {
         streams.start()
         process()
         streams.close()
      }
   }

   fun getProcessor(
       processor: TransformProcessor<TConsume, TProduce>
   ): KStream<String, TProduce> = processor(stream)
}
```

In the above implementation we have an input to our `startStreaming` function called `process`, the `process` function is a callback that needs to call `close` once it is done running. When the `process` function returns the processing will stop, the scope of this function also defines lifecycle of the stream processor and is used for that purpose

So we have defined the processing methodology using a `KStream` but have not provided a way to create a `KStream`. Since the stream can be defined in many different ways, we can define this using a builder class called `StreamBuilder`. This class will be instantiated with the Kafka connection properties and input/output serializers, thereafter it can produce methods for instantiating the `SerializedStream` instance that we can use for data processing

For the sake of our example we will provide a method called `fromTopic` which returns a `SerializedStream` that is configured to work on a single topic, and a `fromTopics` method which will return a `SerializedStream` that listens to multiple topics:

`StreamBuilder.kt`

```kotlin
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
```

An implementation of this interface is as follows:

`StreamBuilder.kt`

```kotlin
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
```

The above class makes use of the `produced` and `consumed` properties which are what Kafka will use for serializing and deserializing data in the stream

And that's about it as far as our implementation for streaming goes

### Using the Stream Processor

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
