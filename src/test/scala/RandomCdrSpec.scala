import java.util.Calendar

import org.specs2.Specification


class RandomCdrSpec extends Specification {
  def is = s2"""

    This is the specification to check the RandonCdr class

    The RandomCdr should
      generate MSISDN of the specified size              $e1
      generate MSISDN with only numeric character        $e2
      generate dateTime in the specified range           $e3
      generate peer MSISDN of the specified size or NONE $e4
      generate peer MSISDN with only numeric character   $e5
      generate callType of VOICE or SMS or DATA          $e6
      generate way of IN or OUT                          $e7
      generate duration lower than 500                   $e8
      generate
                                                         """
  val cdr = RandomCdr(8,86400000)
  val maxDateTime=Calendar.getInstance().getTimeInMillis()-86400000

  def e1 = cdr.msisdn must have length(12)
  def e2 = cdr.msisdn must beMatching("\\+[0-9]+$")
  def e3 = cdr.dateTime must beGreaterThan(maxDateTime)
  def e4 = cdr.peer must have length(12) or beEqualTo("NONE")
  def e5 = cdr.peer must beMatching("\\+[0-9]+$") or beEqualTo("NONE")
  def e6 = cdr.callType must beEqualTo("VOICE") or beEqualTo("SMS") or beEqualTo("DATA")
  def e7 = cdr.way must beEqualTo("IN") or beEqualTo("OUT")
  def e8 = cdr.duration must beLessThan(500)
}
