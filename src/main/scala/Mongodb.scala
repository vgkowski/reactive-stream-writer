import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Seq
import reactivemongo.api.MongoConnection
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentWriter, Macros}

import scala.concurrent.{ExecutionContext, Future}

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
    val mongoUri = "mongodb://" + credentials + host + ":" + port + authenticationSource +"?writeConcernW=majority"
    val driver = new reactivemongo.api.MongoDriver()
    // assign connection to a val to reuse it each time and minimize creation cost
    val connection = driver.connection(mongoUri)

    new Mongodb(connection.get,database,collection,bulkSize,writeParallelism,ec)
  }
}

class Mongodb(connection: MongoConnection, databaseName: String, collectionName: String, bulkSize: Int, writeParallelism: Int, implicit val ec: ExecutionContext) {

  // implicit used to directly write RandomCdr in mongodb without using json
  implicit def randomCdrWriter: BSONDocumentWriter[RandomCdr] = Macros.writer[RandomCdr]

  // don't assign collection to a val to be able to recover a connection failure
  def collection : Future[BSONCollection] = connection.database(databaseName).map(_.collection(collectionName))

  // Akka stream sink writing random CDRs to mongodb at the specified bulk size
  def mongodbBulkSink : Sink[RandomCdr,Future[Done]] = {
    Flow[RandomCdr]
      .grouped(bulkSize)
      .mapAsyncUnordered(writeParallelism){ (bulk : Seq[RandomCdr]) =>
        collection.flatMap(_.insert[RandomCdr](false)(randomCdrWriter,ec).many(bulk)(ec))(ec)
      }
      .toMat(Sink.ignore)(Keep.right)
  }
}
