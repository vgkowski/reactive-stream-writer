import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.{Done, NotUsed}
import com.mongodb.WriteConcern
import com.mongodb.client.model.InsertOneModel
import com.mongodb.reactivestreams.client.{MongoClients, MongoCollection}
import com.typesafe.config._
import com.typesafe.scalalogging._
import org.bson.Document
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import reactivemongo.akkastream._
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

object CdrToMongoReactiveStream {

  implicit def randomCdrWriter: BSONDocumentWriter[RandomCdr] = Macros.writer[RandomCdr]

  def randomCdrThrottledSource(msisdnLength : Int,timeRange : Int, throughput : Int): Source[RandomCdr,NotUsed]= {
    Source
      .fromIterator(() => Iterator.continually(RandomCdr(msisdnLength,timeRange)))
      .throttle(throughput,1.second,1,ThrottleMode.shaping)
      .named("randomCdrThrottledSource")
  }

  def mongodbBulkSink(collection : Future[BSONCollection], bulkSize : Int, ec: ExecutionContext) : Sink[RandomCdr,Future[Done]] = {

    Flow[RandomCdr]
      .grouped(bulkSize)
      .toMat(Sink.foreach{ (bulk: Seq[RandomCdr]) =>
        collection.flatMap(_.insert[RandomCdr](false)(randomCdrWriter,ec).many(bulk)(ec))(ec)
      })(Keep.right)
  }


  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("cdr-data-generator")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = materializer.executionContext

    val logger = Logger("cdr-data-generator")

    val generatorConfig = ConfigFactory.load().getConfig("generator")
    val msisdnLength = generatorConfig.getInt("msisdn-length")
    val bulkSize = generatorConfig.getInt("bulkSize")
    val throughput = generatorConfig.getInt("throughput-per-second")
    val invalidLineProbability = generatorConfig.getDouble("invalid-line-probability")
    val timeRange = generatorConfig.getInt("cdr.timeRange")

    val mongoConfig = ConfigFactory.load().getConfig("mongodb")
    val mongoHost = mongoConfig.getString("host")
    val mongoPort = mongoConfig.getInt("port")
    val authenticationDB = mongoConfig.getString("authenticationDatabase")
    val username = mongoConfig.getString("username")
    val password = mongoConfig.getString("password")
    val databaseName = mongoConfig.getString("database")
    val collectionName = mongoConfig.getString("collection")

    val credentials = if (username != "") username + ":" + password + "@" else ""
    val authenticationSource = if (username != "") "/" + authenticationDB else ""
    val mongoUri = "mongodb://" + credentials + mongoHost + ":" + mongoPort + authenticationSource
    val driver = new reactivemongo.api.MongoDriver()
    val collection = driver.connection(mongoUri).get.database(databaseName).map(_.collection(collectionName))

    logger.info("Starting generation")

    val f = randomCdrThrottledSource(msisdnLength,timeRange,throughput)
      .runWith(mongodbBulkSink(collection,bulkSize,executionContext))

    f.onComplete( r => println("bulkWrite done :"+r.isSuccess.toString))
    logger.info("Generated random data")
  }
}
