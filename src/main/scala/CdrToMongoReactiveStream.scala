import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.mongodb.WriteConcern
import com.mongodb.client.model.InsertOneModel
import com.mongodb.reactivestreams.client.{MongoClients, MongoCollection}
import spray.json._
import com.typesafe.scalalogging._
import com.typesafe.config._
import org.bson.Document

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import collection.JavaConverters._

object CdrToMongoReactiveStream extends App {

  implicit val system = ActorSystem("cdr-data-generator")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext=materializer.executionContext
  import RandomCdrJsonProtocol._

  val generatorConfig = ConfigFactory.load().getConfig("generator")
  val msisdnLength = generatorConfig.getInt("msisdn-length")
  val bulkSize= generatorConfig.getInt("bulkSize")
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

  val credentials = if (username != "") username+":"+password+"@" else ""
  val authenticationSource = if (username != "") "/?authSource="+authenticationDB else ""

  val mongoUri = "mongodb://"+credentials+mongoHost+":"+mongoPort+authenticationSource
  val collection: MongoCollection[Document] =
    MongoClients.create(mongoUri)
      .getDatabase(databaseName)
      .getCollection(collectionName)
      .withWriteConcern(WriteConcern.MAJORITY)

  val logger = Logger("cdr-data-generator")
  logger.info("Starting generation")

  val randomCdrThrottledSource = Source
    .fromIterator(() => Iterator.continually(RandomCdr(msisdnLength,timeRange)))
    .throttle(throughput,1.second,1,ThrottleMode.shaping)
    .named("randomCdrThrottledSource")

  val cdrJsonParseFlow = Flow[RandomCdr]
    .map((cdr: RandomCdr) => cdr.toJson.toString())
    .named("cdrJsonParseFlow")

  val mongodbBulkSink = Flow[String]
    .map((json: String) => Document.parse(json))
    .map((doc: Document) => new InsertOneModel[Document](doc))
    .grouped(bulkSize)
    .flatMapConcat { (docs: Seq[InsertOneModel[Document]]) ⇒
      Source.fromPublisher(collection.bulkWrite(docs.toList.asJava))
    }
    .toMat(Sink.ignore)(Keep.both)


  val f = randomCdrThrottledSource.via(cdrJsonParseFlow).to(mongodbBulkSink).run()

  logger.info("Generated random data")
}
