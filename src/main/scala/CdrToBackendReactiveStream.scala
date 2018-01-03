import akka.actor.ActorSystem
import akka.stream.alpakka.elasticsearch.IncomingMessage
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.alpakka.elasticsearch.scaladsl._
import akka.{Done, NotUsed}
import com.typesafe.config._
import com.typesafe.scalalogging._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object CdrToBackendReactiveStream {

  // Akka stream source generating random CDR at the specified rate
  def randomCdrThrottledSource(msisdnLength : Int,timeRange : Int, throughput : Int): Source[RandomCdr,NotUsed]= {
    Source
      .fromIterator(() => Iterator.continually(RandomCdr(msisdnLength,timeRange)))
      .throttle(throughput,1.second,1,ThrottleMode.shaping)
      .named("randomCdrThrottledSource")
  }

  def main(args: Array[String]): Unit = {

    // implicits for the actor ecosystem and akka stream context
    implicit val system = ActorSystem("cdr-data-generator")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = materializer.executionContext


    val logger = Logger("cdr-data-generator")

    // configuration loading with typesafe config
    val generatorConfig = ConfigFactory.load().getConfig("generator")
    val msisdnLength = generatorConfig.getInt("msisdn-length")
    val throughput = generatorConfig.getInt("throughput-per-second")
    val timeRange = generatorConfig.getInt("cdr.timeRange")

    logger.info("Starting generation")

    Try(args(0)).getOrElse("") match{
      case "elasticsearch" => {
        val es = Elasticsearch(ConfigFactory.load().getConfig("elasticsearch"))
        randomCdrThrottledSource(msisdnLength,timeRange,throughput)
          .async
          .runWith(es.elasticsearchBulkSink)
      }
      case "mongodb" => {
        val mongo = Mongodb(ConfigFactory.load().getConfig("mongodb"),executionContext)
        randomCdrThrottledSource(msisdnLength,timeRange,throughput)
          .async
          .runWith(mongo.mongodbBulkSink)
      }
      case _ => logger.error("you must specify a backend among mongodb and elasticsearch")
    }

    logger.info("Generated random data")
  }
}
