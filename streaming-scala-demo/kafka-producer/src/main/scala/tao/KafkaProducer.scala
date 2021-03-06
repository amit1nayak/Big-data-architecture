package tao

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Attributes, Materializer}
import akka.util.ccompat.JavaConverters
import io.confluent.examples.clients.basicavro.Payment
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializerConfig, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}

import scala.concurrent.Future

object KafkaProducer {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val actorMaterializer: Materializer = ActorMaterializer()

    val config = actorSystem.settings.config.getConfig("akka.kafka.producer")

    val kafkaAvroSerDeConfig = Map[String, Any] (
      AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> "http://localhost:8081",
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> true.toString,
      AbstractKafkaAvroSerDeConfig.AUTO_REGISTER_SCHEMAS -> true.toString
    )

    val producerSettings: ProducerSettings[String, SpecificRecord] = {
      val kafkaAvroSerializer = new KafkaAvroSerializer()
      kafkaAvroSerializer.configure(JavaConverters.mapAsJavaMap(kafkaAvroSerDeConfig), false)
      val serializer = kafkaAvroSerializer.asInstanceOf[Serializer[SpecificRecord]]

      ProducerSettings(actorSystem, new StringSerializer, serializer)
    }

    val done: Future[Done] =
      Source(1 to 100)
        .map(_.toString)
        .map(id=> new Payment(id, Math.random()))
        .log("a test")
        .addAttributes( Attributes.logLevels(
          onElement = Attributes.LogLevels.Info,
          onFailure = Attributes.LogLevels.Error,
          onFinish = Attributes.LogLevels.Info )
        )
        .map(value => new ProducerRecord[String, SpecificRecord]("test", value))
        .runWith(Producer.plainSink(producerSettings))
    //     .runWith(Producer.plainSink(producerSettings))
  }

}
