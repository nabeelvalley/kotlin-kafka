import Config
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import java.util.*

fun createTopic(
    properties: Properties,
    topic: String,
    numPartitions: Int? = null,
    replicationFactor: Short? = null,
) {
    val newTopic = NewTopic(
        topic,
        Optional.ofNullable(numPartitions),
        Optional.ofNullable(replicationFactor)
    )

    AdminClient.create(properties).use { client ->
        client.createTopics(listOf(newTopic))
    }
}


interface IAdmin {
    fun createTopic(topic: String)
}

class Admin(properties: Properties) : Config(properties), IAdmin {
    override fun createTopic(topic: String) = createTopic(properties, topic)
}