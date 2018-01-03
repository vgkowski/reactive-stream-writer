import akka.Done
import akka.stream.alpakka.elasticsearch.IncomingMessage
import akka.stream.alpakka.elasticsearch.scaladsl.{ElasticsearchSink, ElasticsearchSinkSettings}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.typesafe.config._
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.elasticsearch.client.{RestClient, RestClientBuilder}

import scala.concurrent.Future

object Elasticsearch{

  def apply(conf: Config): Elasticsearch ={
    // load configuration from typesafe config
    val protocol = conf.getString("protocol")
    val host = conf.getString("host")
    val port = conf.getInt("port")
    val index = conf.getString("index")
    val docType = conf.getString("doc-type")
    val bulkSize = conf.getInt("bulk-size")
    val username = conf.getString("username")
    val password = conf.getString("password")

    println(" connection info ", protocol,host,port,index,docType,username,password)
    // customize the ES client builder to authenticate
    val credentialsProvider = new BasicCredentialsProvider()
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username,password))
    val builder = RestClient.builder(new HttpHost(host, port,protocol)).setHttpClientConfigCallback(
      (httpClientBuilder: HttpAsyncClientBuilder) => httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
    // create the ES backend
    new Elasticsearch(index,docType,bulkSize,builder.build())
  }
}


class Elasticsearch(index: String, docType: String, bulkSize: Int, implicit val httpClient: RestClient){

  import RandomCdrJsonProtocol._

  // Akka stream sink writing random CDRs to elasticsearch at the specified bulk size
  def elasticsearchBulkSink : Sink[RandomCdr,Future[Done]] = {
    Flow[RandomCdr]
      .map{cdr: RandomCdr =>
        IncomingMessage(Some(cdr.msisdn+"-"+cdr.peer+"-"+cdr.dateTime),cdr)
      }
      .toMat(ElasticsearchSink.typed[RandomCdr]
        (index,docType,ElasticsearchSinkSettings(bulkSize,5000,100,true)))(Keep.right)
  }

}
