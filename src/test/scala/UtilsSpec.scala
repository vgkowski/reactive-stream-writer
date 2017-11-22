import org.specs2.Specification

class UtilsSpec extends Specification {

  def is = s2"""

    This is the specifcartion to check Utils class

    The randomString should
      generate numeric string only                        $e1
      generate string with the specified length           $e2
                                                          """

  def e1 = Utils.randomString(8) must beMatching("[0-9]+$")
  def e2 = Utils.randomString(20) must have length(20)
}
