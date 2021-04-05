package goldrush

import goldrush.GoRandomContants._

object GoRndSource {

  val rngLen = 607
  val rngTap = 273
  val rngMax: Long = 1L << 63L
  val rngMask: Long = rngMax - 1
  val int32max = (1 << 31) - 1

  // rngCooked used for seeding. See gen_cooked.go for details.

  def calcAndPrint[T](funcName: String)(f: () => T): T = {
//    println(funcName + " started")
    val res = f()
//    Thread.sleep(500)
//    println(funcName + s" ended, res: $res")
    res
  }

  type int32 = Int
  type int = Long
  type int64 = Long
  type uint32 = BigInt
  type uint64 = BigInt

  def checkedCast[T, K](l: T, f: T => K, casterT: T => BigInt, casterK: K => BigInt): K = {
    val res = f(l)
//    assert(casterT(l) == casterK(res), s"$l $res")
//    println(s"casted: ${l} to $res")
    res
  }

  def int(l: Long): int = checkedCast[Long, Int](l, _.toInt, BigInt.apply, BigInt.apply)
  def int(l: uint64): int = checkedCast[uint64, Int](l, _.intValue, identity, BigInt.apply)
  def int32(l: Long): int32 = checkedCast[Long, Int](l, _.toInt, BigInt.apply, BigInt.apply)
  def int32(l: uint32): int32 = checkedCast[uint32, int32](l, _.toInt, identity, BigInt.apply)
  def uint32(l: Int): uint32 = {
    val f = {r: Int => BigInt.apply(r)}
    checkedCast[Int, uint32](l, f, BigInt.apply, identity)
  }
  def int64(l: Long): int64 = l
  def int64(l: uint64): int64 =
    checkedCast[uint64, int64](l, _.longValue, identity, BigInt.apply)
  def uint64(input: Long): BigInt = calcAndPrint("uint64(l: Long)") { () =>
    import java.math.BigInteger
    val TWO_64 = BigInteger.ONE.shiftLeft(64)

    def asUnsignedDecimalString(l: Long): String = {
      var b = BigInteger.valueOf(l)
      if (b.signum < 0) b = b.add(TWO_64)
      b.toString
    }

    val l = BigInt.apply(java.lang.Long.toUnsignedString(input))
    val r = asUnsignedDecimalString(input)
//    assert(l == BigInt.apply(r))
    l
  }

  class RngSource {
    var tap: int = 0L // index into vec
    var feed: int = 0L // index into vec
    var vec = Array.ofDim[int64](rngLen) // current feedback register
  }

  implicit class RngSourceExtension(rng: RngSource) {

    // Int63 returns a non-negative pseudo-random 63-bit integer as an int64.
    def Int63(): int64 = calcAndPrint("Int63(): int64 - 72") { () =>
      int64(Uint64() & rngMask)
    }

    // Uint64 returns a non-negative pseudo-random 64-bit integer as an uint64.
    def Uint64(): uint64 = calcAndPrint("Uint64 - 77") { () =>
      rng.tap -= 1
      if (rng.tap < 0) {
        rng.tap += rngLen
      }

      rng.feed -= 1
      if (rng.feed < 0) {
        rng.feed += rngLen
      }

      var x = rng.vec.apply(int32(rng.feed)) + rng.vec(int32(rng.tap))
      rng.vec(int32(rng.feed)) = x
      uint64(x)
    }

    // Seed uses the provided seed value to initialize the generator to a deterministic state.
    def Seed(inputSeed: int64): Unit = calcAndPrint("def Seed(inputSeed: int64) - 94") { () =>
      var seed = inputSeed
      rng.tap = 0L
      rng.feed = rngLen - rngTap

      seed = seed % int32max
      if (seed < 0) {
        seed += int32max
      }
      if (seed == 0) {
        seed = 89482311L
      }

      var x = int32(seed)
      var i = -20
      while (i < rngLen) {
        x = seedrand(x)
        if (i >= 0) {
          var u: int64 = 0L
          u = int64(x) << 40
          x = seedrand(x)
          u ^= int64(x) << 20
          x = seedrand(x)
          u ^= int64(x)
          u ^= rngCooked(i)
          rng.vec(i) = u
        }
        i += 1
      }
    }

  }

  def panic(s: String) = throw new RuntimeException(s)

  // seed rng x[n+1] = 48271 * x[n] mod (2**31 - 1)
  def seedrand(inputX: int32): int32 = {
    var x = inputX
    val A = 48271
    val Q = 44488
    val R = 3399

    val hi = x / Q
    val lo = x % Q
    x = A * lo - R * hi
    if (x < 0) {
      x += int32max
    }
    x
  }

  def NewSource(seed: int64): RngSource = {
    var rng: RngSource = new RngSource()
    rng.Seed(seed)
    rng
  }

  case class Rand(src: RngSource)

  implicit class RadnomExtension(r: Rand) {

    def Int63(): int64 = calcAndPrint("Int63(): int64 - 156") { () =>
      return r.src.Int63()
    }

    def Int31(): int32 = calcAndPrint("Int31(): int32 - 160") { () =>
      return int32(r.Int63() >> 32)
    }

    def Int31n(n: int32): int32 = calcAndPrint("Int31n(n: int32)") { () =>
      if (n <= 0) {
        panic("invalid argument to Int31n")
      }
      if ((n & (n - 1)) == 0) { // n is power of two, can mask
        return r.Int31() & (n - 1)
      }
      var max = int32(((1L << 31L) - 1) - ((1L << 31L) % uint32(n)))
      var v = r.Int31()
      while (v > max) {
        v = r.Int31()
      }
      return v % n
    }

    def Int63n(n: int64): int64 = {
      println("Int63n")
      if (n <= 0) {
        panic("invalid argument to Int63n")
      }
      if ((n & (n - 1)) == 0) { // n is power of two, can mask
        return r.Int63() & (n - 1)
      }
      var max = int64((1 << 63) - 1 - (1 << 63) % uint64(n))
      var v = r.Int63()
      while (v > max) {
        v = r.Int63()
      }
      v % n
    }

    def Intn(n: int): int = calcAndPrint("Intn(n: int): int") { () =>
      if (n <= 0) {
        panic("invalid argument to Intn")
      }
      if (n <= (1 << 31) - 1) {
        return int(r.Int31n(int32(n)))
      }
      return int(r.Int63n(int64(n)))
    }
  }

  def main(args: Array[String]): Unit = {

    val rndSource = NewSource(14L)
    val rnd = Rand(rndSource)

    val begin = System.nanoTime()
    val rnds = Seq.fill(1_000_000)(rnd.Intn(152342342L))
    val end = System.nanoTime()

    println(s"${(end - begin) / 1e6.toInt}ms for ${rnds.size} numbers")

  }

}
