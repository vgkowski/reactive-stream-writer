import java.util.Calendar
import scala.util.Random
import spray.json._


object RandomCdr{

  val callArray= Array("VOICE","SMS","DATA")
  val wayArray= Array("IN","OUT")

  def apply(idLength: Int,timeRange: Int)={
    val msisdn = "+336"+Utils.randomString(idLength)
    val callType= callArray(Random.nextInt(3))
    val tuple= callType match {
      case "VOICE" => ("+336"+Utils.randomString(idLength),wayArray(Random.nextInt(2)),Random.nextInt(500),0,0)
      case "SMS" => ("+336"+Utils.randomString(idLength),wayArray(Random.nextInt(2)),0,0,0)
      case "DATA" => ("NONE","OUT",0, Random.nextInt(10000000),Random.nextInt(10000000))
    }
    new RandomCdr(
      msisdn,
      Calendar.getInstance().getTimeInMillis()-Random.nextInt(timeRange),
      tuple._1,
      callType,
      tuple._2,
      tuple._3,
      tuple._4,
      tuple._5
    )
  }
}

object RandomCdrJsonProtocol extends DefaultJsonProtocol{
  implicit val randomCdrFormat = jsonFormat8(RandomCdr.apply)
}

case class RandomCdr(msisdn: String, dateTime: Long, peer: String, callType: String, way: String, duration: Int, byteUp: Int, byteDown: Int)

