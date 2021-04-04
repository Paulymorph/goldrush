package goldrush

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import goldrush.GoldStore.Coin

import scala.concurrent.duration._

class Statistics[F[_]](store: Ref[F, StatisticsInfo]) {
  def issuedLicense(spent: Seq[Coin], license: License, time: FiniteDuration): F[Unit] =
    store.update { info =>
      val spentThisTime = spent.size
      val currentCapacity = license.digAllowed - license.digUsed
      val newLicenseStats = {
        val LicenseStats(count, min, max, sum, durationSum) =
          info.spentCoins.getOrElse(spentThisTime, LicenseStats(0, Int.MaxValue, 0, 0, 0.millis))
        LicenseStats(
          count + 1,
          Math.min(min, currentCapacity),
          Math.max(max, currentCapacity),
          sum + currentCapacity,
          durationSum + time
        )
      }
      info.copy(
        issuedLicenses = info.issuedLicenses + 1,
        licensesCapacity = info.licensesCapacity + currentCapacity,
        spentCoins = info.spentCoins + (spentThisTime -> newLicenseStats)
      )
    }

  def digged(time: FiniteDuration): F[Unit] = store.update { info =>
    info.copy(digTimes = info.digTimes + 1, digsDuration = info.digsDuration + time)
  }

  def cashed(treasureSize: Int, time: FiniteDuration): F[Unit] = store.update { info =>
    info.copy(
      foundCoins = info.foundCoins + treasureSize,
      cashTimes = info.cashTimes + 1,
      cashDuration = info.cashDuration + time
    )
  }

  val getInfo: F[StatisticsInfo] = store.get
}

case class StatisticsInfo(
    issuedLicenses: Int,
    licensesCapacity: Int,
    spentCoins: Map[Int, LicenseStats],
    digTimes: Int,
    digsDuration: FiniteDuration,
    foundCoins: Int,
    cashTimes: Int,
    cashDuration: FiniteDuration
)

case class LicenseStats(count: Int, min: Int, max: Int, sum: Int, durationSum: FiniteDuration)

object Statistics {
  def apply[F[_]: Sync] =
    Ref
      .of(StatisticsInfo(0, 0, Map.empty, 0, 0.millis, 0, 0, 0.millis))
      .map(new Statistics[F](_))
}
