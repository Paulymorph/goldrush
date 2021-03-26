package goldrush

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._

class Statistics[F[_]](store: Ref[F, StatisticsInfo]) {
  def issuedLicense(license: License): F[Unit] =
    store.update { info =>
      info.copy(
        issuedLicenses = info.issuedLicenses + 1,
        licensesCapacity = info.licensesCapacity + license.digAllowed - license.digUsed
      )
    }

  val getInfo: F[StatisticsInfo] = store.get
}

case class StatisticsInfo(issuedLicenses: Int, licensesCapacity: Int)

object Statistics {
  def apply[F[_]: Sync] =
    Ref
      .of(StatisticsInfo(0, 0))
      .map(new Statistics[F](_))
}
