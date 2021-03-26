package goldrush

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import goldrush.GoldStore.Coin

class Statistics[F[_]](store: Ref[F, StatisticsInfo]) {
  def issuedLicense(spent: Seq[Coin], license: License): F[Unit] =
    store.update { info =>
      info.copy(
        issuedLicenses = info.issuedLicenses + 1,
        licensesCapacity = info.licensesCapacity + license.digAllowed - license.digUsed,
        spentCoins = info.spentCoins + spent.size
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
    spentCoins: Int,
    digTimes: Int
) {
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
      .of(StatisticsInfo(0, 0, 0, 0))
      .map(new Statistics[F](_))
}
