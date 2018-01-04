import java.util.Calendar

import org.scalatest.{Matchers, WordSpec}

class RandomCdrSpec extends WordSpec with Matchers {

  "RandomCdr.apply" should {
    val cdr = RandomCdr(8,86400000)
    val maxDateTime=Calendar.getInstance().getTimeInMillis()-86400000
    "generate MSISDN of the specified size" in {
      cdr.msisdn.length == 12
    }
    "generate MSISDN with only numeric character" in {
      cdr.msisdn.matches("\\+[0-9]+$")
    }
    "generate dateTime in the specified range" in {
      cdr.dateTime > maxDateTime
    }
    "generate peer MSISDN of the specified size or NONE" in {
      (cdr.peer.length == 12) || (cdr.peer == "NONE")
    }
    "generate peer MSISDN with only numeric character or NONE" in {
      (cdr.peer.matches("\\+[0-9]+$")) || (cdr.peer == "NONE")
    }
    "generate callType of VOICE or SMS or DATA " in {
      Seq("VOICE", "SMS", "DATA").contains(cdr.callType)
    }
    "generate way of IN or OUT" in {
      Seq("IN", "OUT").contains(cdr.way)
    }
    "generate duration lower than 500" in {
      cdr.duration < 500
    }
  }
}
