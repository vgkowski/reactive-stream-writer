import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.{Done, NotUsed}
import com.typesafe.config._
import com.typesafe.scalalogging._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object CdrToMongoReactiveStream {

  // implicit used to directly write RandomCdr in mongodb without using json
  implicit def randomCdrWriter: BSONDocumentWriter[RandomCdr] = Macros.writer[RandomCdr]

  // Akka stream source generating random CDR at the specified rate
  def randomCdrThrottledSource(msisdnLength : Int,timeRange : Int, throughput : Int): Source[RandomCdr,NotUsed]= {
    Source
      .fromIterator(() => Iterator.continually(RandomCdr(msisdnLength,timeRange)))
      .throttle(throughput,1.second,1,ThrottleMode.shaping)
      .named("randomCdrThrottledSource")
  }

  // Akka stream sink writing random CDRs to mongodb at the specified bulk size
  def mongodbBulkSink(collection : Future[BSONCollection], bulkSize : Int,writeParallelism: Int, ec: ExecutionContext) : Sink[RandomCdr,Future[Done]] = {
    Flow[RandomCdr]
      .grouped(bulkSize)
      .mapAsyncUnordered(writeParallelism){ (bulk : Seq[RandomCdr]) =>
        collection.flatMap(_.insert[RandomCdr](false)(randomCdrWriter,ec).many(bulk)(ec))(ec)
      }
      .toMat(Sink.ignore)(Keep.right)
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
    val bulkSize = generatorConfig.getInt("bulkSize")
    val writeParallelism = generatorConfig.getInt("write-parallelism")
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

    // mongodb connection string using credentials or not
    val credentials = if (username != "") username + ":" + password + "@" else ""
    val authenticationSource = if (username != "") "/" + authenticationDB else ""
    val mongoUri = "mongodb://" + credentials + mongoHost + ":" + mongoPort + authenticationSource +"?writeConcernW=majority"
    // connection setup (returns futures)
    val driver = new reactivemongo.api.MongoDriver()
    val collection = driver.connection(mongoUri).get.database(databaseName).map(_.collection(collectionName))

    logger.info("Starting generation")

    // graph building from source to sink
    val f = randomCdrThrottledSource(msisdnLength,timeRange,throughput).async
      .runWith(mongodbBulkSink(collection,bulkSize,writeParallelism,executionContext))

    //f.onComplete( r => println("bulkWrite done :"+r.isSuccess.toString))
    logger.info("Generated random data")
  }
}
