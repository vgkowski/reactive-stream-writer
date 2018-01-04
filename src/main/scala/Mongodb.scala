import akka.{Done, NotUsed}
import akka.stream.alpakka.mongodb.scaladsl.MongoSink
import akka.stream.scaladsl.{Flow, Keep, RestartSink, Sink}
import com.typesafe.config.Config
import org.mongodb.scala.{Document, MongoClient, MongoCollection, WriteConcern}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object Mongodb{
  def apply(conf: Config,ec: ExecutionContext): Mongodb ={
    // load configuration from typesafe config
    val host = conf.getString("host")
    val port = conf.getInt("port")
    val authDatabase = conf.getString("authenticationDatabase")
    val database = conf.getString("database")
    val collection = conf.getString("collection")
    val bulkSize = conf.getInt("bulk-size")
    val writeParallelism = conf.getInt("write-parallelism")
    val username = conf.getString("username")
    val password = conf.getString("password")

    // builds the mongodb connection string and open connection
    val credentials = if (username != "") username + ":" + password + "@" else ""
    val authenticationSource = if (username != "") "/" + authDatabase else ""
    val mongoUri = "mongodb://" + credentials + host + ":" + port + authenticationSource
    val client = MongoClient(mongoUri)
    val db = client.getDatabase(database)
    val coll = db.getCollection(collection).withWriteConcern(WriteConcern.MAJORITY)

    new Mongodb(coll,bulkSize,writeParallelism,ec)
  }
}

class Mongodb(coll: MongoCollection[Document], bulkSize: Int, writeParallelism: Int, implicit val ec: ExecutionContext) {

  import RandomCdrJsonProtocol._
  import spray.json._

  // Akka stream sink writing random CDRs to mongodb at the specified bulk size
  def mongodbBulkSink : Sink[RandomCdr,NotUsed] = {
    Flow[RandomCdr]
      .map(cdr => Document(cdr.toJson.toString()))
      .async
      .grouped(bulkSize)
      .to {
        RestartSink.withBackoff(
          minBackoff = 3.seconds,
          maxBackoff = 30.seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        ) { () ⇒
          MongoSink.insertMany(parallelism = writeParallelism, collection = coll)
        }
      }
  }
}
