import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, RestartSink, Sink}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Future
import scala.concurrent.duration._


object Kafka {

  def apply(conf: Config,system: ActorSystem): Kafka ={
    val topic = conf.getString("topic")
    val bootstrapServer = conf.getString("bootstrap-servers")
    val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServer)
    new Kafka(topic, producerSettings)
  }

}

class Kafka(topic: String, producerSettings: ProducerSettings[Array[Byte],String]) {

  val kafkaSink : Sink[RandomCdr,NotUsed]={
    Flow[RandomCdr]
      .map(cdr => new ProducerRecord[Array[Byte],String](topic, cdr.toString))
      .to {
        // restart policy is required because the connector doesn't natively manage errors
        RestartSink.withBackoff(
          minBackoff = 3.seconds,
          maxBackoff = 30.seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        ) { () ⇒
          Producer.plainSink(producerSettings)
        }
      }
  }
}
