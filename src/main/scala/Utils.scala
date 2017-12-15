import scala.collection.immutable.Stream
import scala.util.Random

object Utils {
  private val random = new Random()

  def randomString(length: Int = 10): String =
    alphanumeric.take(length).mkString

  def alphanumeric: Stream[Char] = {
    def nextAlphaNum: Char = {
      val chars = "0123456789"
      chars charAt (random nextInt chars.length)
    }

    Stream continually nextAlphaNum
  }
}