import org.scalatest.{Matchers, WordSpec}

class UtilsSpec extends WordSpec with Matchers {

  "Utils.randomString" should {
    val str = Utils.randomString(8)
    "generate numeric string only" in {
      str.matches("[0-9]+$")
    }
    "generate string with the specified length" in {
      str.length == 8
    }
  }
}
