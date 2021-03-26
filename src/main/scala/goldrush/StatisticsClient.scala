package goldrush

import cats.FlatMap
import cats.syntax.flatMap._

class StatisticsClient[F[_]: FlatMap] private (underlying: Client[F], statistics: Statistics[F])
    extends Client[F] {
  override def getBalance(): F[Balance] = underlying.getBalance()

  override def listLicenses(): F[Seq[License]] = underlying.listLicenses()

  override def issueLicense(coins: Int*): F[License] =
    underlying.issueLicense().flatTap(l => statistics.issuedLicense(coins, l))

  override def explore(area: Area): F[ExploreResponse] = underlying.explore(area)

  override def dig(licenseId: Int, posX: Int, posY: Int, depth: Int): F[Seq[String]] =
    underlying.dig(licenseId, posX, posY, depth)

  override def cash(treasure: String): F[Seq[Int]] =
    underlying.cash(treasure)
}

object StatisticsClient {
  def wrap[F[_]: FlatMap](statistics: Statistics[F])(client: Client[F]) =
    new StatisticsClient[F](client, statistics)
}
