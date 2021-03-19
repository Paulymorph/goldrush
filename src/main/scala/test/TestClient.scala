package test

import cats.effect.Timer
import goldrush.{Area, Balance, Client, ExploreResponse, License}

class TestClient[F[_]: Timer] extends Client[F] {
  override def getBalance(): F[Balance] = ???
  override def listLicenses(): F[Seq[License]] = ???
  override def issueLicense(coins: Int*): F[License] = ???
  override def explore(area: Area): F[ExploreResponse] = ???
  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = ???
  override def cash(treasure: String): F[Seq[Int]] = ???
}
