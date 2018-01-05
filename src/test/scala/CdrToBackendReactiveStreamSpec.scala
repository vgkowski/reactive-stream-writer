import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl._
import org.apache.http.HttpHost
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.codecs.{DEFAULT_CODEC_REGISTRY, Macros}
import org.scalatest._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class CdrToBackendReactiveStreamSpec extends WordSpec with Matchers {

  import concurrent.Eventually._

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
      val randomCdrCodecProvider = Macros.createCodecProvider[RandomCdr]()
      val codecRegistry = fromRegistries( fromProviders(randomCdrCodecProvider), DEFAULT_CODEC_REGISTRY )

      val client = MongoClient("mongodb://localhost:27017")
      val db = client.getDatabase("cdrDB")
      val coll = db.getCollection("cdr")
      val cdrColl = db.getCollection[RandomCdr]("cdr").withCodecRegistry(codecRegistry)

      db.drop().head().onComplete(f => println("db dropped"))

      val mongodb = new Mongodb(coll,1,1,executionContext)
      val (probe, future) = TestSource.probe[RandomCdr]
        .toMat(mongodb.mongodbBulkSink)(Keep.both)
        .run()

      probe.sendNext(randomCdr)
      probe.sendComplete()
      eventually(timeout(2 seconds), interval(500 millis)){ Await.result(cdrColl.find().head(),5.second) shouldEqual randomCdr }
      db.drop().head().onComplete(f => println("db dropped"))
    }
  }

  "elasticsearchBulkSink" should {
    "bulk write json document to Elasticsearch" in {
      import RandomCdrJsonProtocol._
      val lowClient = RestClient.builder(new HttpHost("localhost", 9200)).build()
      val highClient = new RestHighLevelClient(lowClient)
      Try(highClient.delete(new DeleteRequest("cdr")))

      val es = new Elasticsearch("cdr","cdr",1,lowClient)

      val (probe, future) = TestSource.probe[RandomCdr]
        .toMat(es.elasticsearchBulkSink)(Keep.both)
        .run()

      probe.sendNext(randomCdr)
      probe.sendComplete()

      eventually(timeout(2 seconds), interval(500 millis)) {
        val result = highClient.search(new SearchRequest("cdr")).getHits.getAt(0).getSourceAsString.parseJson.convertTo[RandomCdr]
        result shouldEqual randomCdr
      }
      lowClient.performRequest("DELETE","/cdr")
    }
  }
}
