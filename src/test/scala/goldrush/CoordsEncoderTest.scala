package goldrush

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CoordsEncoderTest extends AnyFlatSpec with Matchers {

  val encoder = new HexCoordsEncoder

  "decode" should "be able to decode encoded result" in {
    for {
//      i <- Seq(0, 1, 15, 692, 2615, 3500, 3501) ++ Seq.fill(100)(Random.nextInt(3500))
//      j <- Seq(0, 1, 96, 288, 261, 3500, 3501) ++ Seq.fill(100)(Random.nextInt(3500))
      i <- Seq.range(0, 3600)
      j <- Seq.range(0, 3600)
      k <- Seq.range(0, 11)
    } {
      encoder.decode(encoder.encode(i, j, k)) shouldBe (i, j, k)
    }
  }

}
