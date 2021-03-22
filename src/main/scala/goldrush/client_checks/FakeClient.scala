package goldrush.client_checks

import java.util.concurrent.ConcurrentHashMap

import cats.effect.Timer
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, ApplicativeError, FlatMap, Foldable, MonadError}
import goldrush.Types.{ThrowableApplicativeError, ThrowableMonadError}
import goldrush._
import goldrush.client_checks.FakeClient._

import scala.util.{Random, Try}

class FakeClient[F[_]: ThrowableMonadError: Timer] extends Client[F] {

  private lazy val gameField: ConcurrentHashMap[Int, ConcurrentHashMap[
    Int,
    ConcurrentHashMap[Int, Boolean]
  ]] = {

    def genJavaMap[V](kN: Int, vFunc: () => V): ConcurrentHashMap[Int, V] = {
      val map = new ConcurrentHashMap[Int, V]()
      Range(0, kN).foreach { k =>
        map.put(k, vFunc())
      }
      map
    }

    genJavaMap(
      fieldSize,
      () =>
        genJavaMap(
          fieldSize,
          () => genJavaMap(fieldDepth, () => Random.nextInt(250) == 0)
        )
    )

  }

  private lazy val licences = new ConcurrentHashMap[Int, Int](10)
  private lazy val existingTreasure = new ConcurrentHashMap[String, Boolean]()
  private lazy val treasureBalance = new ConcurrentHashMap[String, Int]()

  override def getBalance(): F[Balance] = ???

  override def listLicenses(): F[Seq[License]] = ???

  override def issueLicense(coins: Int*): F[License] = {
    val res =
      if (licences.size() >= maxLicencesAllowed)
        Left(new RuntimeException("Too many licences"))
      else {
        val l = License(Random.nextInt(), Math.max(3, coins.size * 3), 0)
        licences.put(l.id, l.digAllowed)
        Right(l)
      }
    fakeWaitAndResponse(
      res,
      200,
      100,
      methodName = "issueLicense"
    ).map { result =>
      println(s"licences size: ${licences.size()}")
      if (licences.size() >= maxLicencesAllowed)
        licences.put(result.id, result.digAllowed)
      result
    }
  }

  override def explore(area: Area): F[ExploreResponse] = {
    if (area.posX + area.sizeX > fieldSize || area.posY + area.sizeY > fieldSize)
      throw new IllegalArgumentException(s"Exploring outside of area: $area")

    val treasures = area.locations.map { case (x, y) =>
      gameField.get(x).get(y).values().stream().filter(x => x).count()
    }.sum

    fakeWaitAndResponse(
      Right(ExploreResponse(area, treasures.toInt)),
      450,
      350,
      methodName = "explore"
    )
  }

  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = {
    val cell = gameField.get(posX).get(posY)
    val vCurrent = cell.get(depth)

    val licenceLeft = licences.getOrDefault(licenseId, -1)
    for {
      s <-
        if (licenceLeft == 1) {
          Applicative[F].pure(licences.remove(licenseId, licenceLeft))
        } else if (licenceLeft == -1) {
          ApplicativeError[F, Throwable].raiseError(
            new RuntimeException(s"$licenseId does not exists")
          )
        } else {
          compareAndSet(licences, licenseId, licenceLeft, licenceLeft - 1)
        }

      treasureId <- compareAndSet(cell, depth, vCurrent, false)
        .map { _ =>
          val treasureId = Random.nextString(10)
          existingTreasure.put(treasureId, false)
          treasureBalance.put(treasureId, Random.nextInt(10))
          treasureId
        }

      res <- fakeWaitAndResponse(
        Right(Seq(treasureId)),
        200,
        300,
        methodName = "dig"
      )
    } yield res
  }

  override def cash(treasure: String): F[Seq[Int]] = {
    val vCurrent = treasureBalance.get(treasure)

    compareAndSet(treasureBalance, treasure, vCurrent, 0)
      .flatMap(_ =>
        fakeWaitAndResponse(
          Right(Seq(vCurrent)),
          20,
          10,
          methodName = "cash"
        )
      )
  }

  private def compareAndSet[K, V](
      map: ConcurrentHashMap[K, V],
      key: K,
      expected: V,
      newValue: V
  ): F[V] = {
    MonadError[F, Throwable].fromTry(
      Try(
        map.computeIfPresent(
          key,
          { case (_, v) =>
            if (expected != v) {
              val msg = s"Value was $expected, became $v"
              println(msg)
              throw new IllegalStateException(msg)
            } else newValue
          }
        )
      )
    )
  }
}

object FakeClient {

  private def fakeWaitAndResponse[T, F[
      _
  ]: Timer: ThrowableApplicativeError: FlatMap](
      res: => Either[Throwable, T],
      errorLatency: Int,
      successLatency: Int,
      methodName: String = "undefined"
  ): F[T] = {
    def printVal[Z](v: Z) = {
      println(s"$methodName was called. Result: $v")
      v
    }

    val response: F[T] =
      res
        .fold(
          l => CatsUtil.sleepAndReturn(errorLatency, Left(l)),
          r => CatsUtil.sleepAndReturn(successLatency, Right(r))
        )
        .flatMap { r =>
          Applicative[F].pure(printVal(r))
        }

    ApplicativeError[F, Throwable]
      .handleErrorWith(response)(th =>
        ApplicativeError[F, Throwable].raiseError(printVal(th))
      )
  }

  private val fieldSize = 3500
  private val fieldDepth = 10
  private val maxLicencesAllowed = 10

}
