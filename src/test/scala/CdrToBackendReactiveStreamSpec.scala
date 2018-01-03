import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl._
import org.scalatest._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CdrToBackendReactiveStreamSpec extends WordSpec with Matchers {

  import CdrToBackendReactiveStream._

  implicit val system = ActorSystem("cdr-data-generator")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = materializer.executionContext

  val randomCdr = RandomCdr("+33612345678",1511448336402L,"+33612345678","SMS","OUT",0,0,0)



  "randomCdrThrottledSource" should {
    "generate RandomCdr elements only" in {
      val future = CdrToBackendReactiveStream.randomCdrThrottledSource(8,86400000,1)
        .runWith(Sink.head)

      val cdr = Await.result(future,5.second)
      cdr shouldBe a [RandomCdr]
    }
  }

  "mongoBulkSink" should {
    "bulk write json document to MongoDB" in {
      implicit def randomCdrReader: BSONDocumentReader[RandomCdr] = Macros.reader[RandomCdr]
      implicit def randomCdrWriter: BSONDocumentWriter[RandomCdr] = Macros.writer[RandomCdr]

      val driver = new reactivemongo.api.MongoDriver()
      val connection = driver.connection("mongodb://localhost:27017").get
      val database = connection.database("cdrDB")
      val collection : Future[BSONCollection]= database.map(_.collection("cdr"))
      val mongodb = new Mongodb(connection,"cdrDB","cdr",1,1,executionContext)

      val (probe, future) = TestSource.probe[RandomCdr]
        .toMat(mongodb.mongodbBulkSink)(Keep.both)
        .run()

      probe.sendNext(randomCdr)
      probe.sendComplete()

      val result = for {
        write <- future
        read <- collection.flatMap(_.find(randomCdr).one[RandomCdr])
      } yield read
      Await.result(result,5.second).get shouldEqual randomCdr
      database.foreach(_.drop())
    }
  }
}
