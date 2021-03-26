package goldrush

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import goldrush.GoldStore.Coin

class Statistics[F[_]](store: Ref[F, StatisticsInfo]) {
  def issuedLicense(spent: Seq[Coin], license: License): F[Unit] =
    store.update { info =>
      val spentThisTime = spent.size
      val currentCapacity = license.digAllowed - license.digUsed
      val newLicenseStats = {
        val LicenseStats(count, min, max, sum) =
          info.spentCoins.getOrElse(spentThisTime, LicenseStats(0, Int.MaxValue, 0, 0))
        LicenseStats(
          count + 1,
          Math.min(min, currentCapacity),
          Math.max(max, currentCapacity),
          sum + currentCapacity
        )
      }
      info.copy(
        issuedLicenses = info.issuedLicenses + 1,
        licensesCapacity = info.licensesCapacity + currentCapacity,
        spentCoins = info.spentCoins + (spentThisTime -> newLicenseStats)
      )
    }

  def digged(): F[Unit] = store.update { info =>
    info.copy(digTimes = info.digTimes + 1)
  }

  val getInfo: F[StatisticsInfo] = store.get
}

case class StatisticsInfo(
    issuedLicenses: Int,
    licensesCapacity: Int,
    spentCoins: Map[Int, LicenseStats],
    digTimes: Int
) {
  override def toString: String = this.productElementNames
    .zip(this.productIterator)
    .map { case (name, value) =>
      s"$name = $value"
    }
    .mkString(", ")
}

case class LicenseStats(count: Int, min: Int, max: Int, sum: Int) {
  override def toString: String = this.productElementNames
    .zip(this.productIterator)
    .map { case (name, value) =>
      s"$name = $value"
    }
    .mkString(", ")
}

object Statistics {
  def apply[F[_]: Sync] =
    Ref
      .of(StatisticsInfo(0, 0, Map.empty, 0))
      .map(new Statistics[F](_))
}
