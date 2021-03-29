package goldrush

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.{Clock, Concurrent, Sync}
import cats.effect.concurrent.{Deferred, Ref}

import scala.util.Random
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.apply._
import goldrush.TestAndLearn.{BestDecision, CalculatingBest, Probe}

import scala.concurrent.duration.FiniteDuration

object Issuer {
  type Issuer[F[_]] = F[License]

  def free[F[_]](client: Client[F]): Issuer[F] = {
    client.issueLicense()
  }

  def paid[F[_]: Monad](howMany: Int, client: Client[F], store: GoldStore[F]): Issuer[F] = {
    for {
      coins <- store.tryTake(howMany)
      license <- client.issueLicense(coins: _*)
    } yield license
  }

  def paidRandom[F[_]: Monad: Sync](
      max: Int,
      client: Client[F],
      store: GoldStore[F]
  ): Issuer[F] = {
    for {
      r <- Sync[F].delay(Random.nextInt(max + 1))
      coins <- store.tryTake(r)
      license <- client.issueLicense(coins: _*)
    } yield license
  }

  def dynamic[F[_]: Concurrent: Clock](
      probeInterval: FiniteDuration,
      client: Client[F],
      store: GoldStore[F],
      statistics: Statistics[F]
  ): F[Issuer[F]] = {
    def tryNextCosts(previousBest: Int): NonEmptyList[Int] = {
      val checkNeighbours =
        List(
          previousBest / 2,
          previousBest * 0.75,
          previousBest * 0.9,
          previousBest * 1.1,
          previousBest * 1.5
        ).map(_.toInt).filter(_ <= 1000)
      (NonEmptyList.of(0, 1, 5, 15) ++ checkNeighbours).distinct
    }

    val bestCost = statistics.getInfo.map {
      case StatisticsInfo(_, _, spentCoins, digTimes, foundCoins) =>
        val expectedDigProfit = foundCoins / digTimes
        spentCoins.maxBy { case (cost, LicenseStats(count, _, _, sum)) =>
          val expectedLicensesAvailable = sum / count
          expectedDigProfit * expectedLicensesAvailable - cost
        }._1
    }

    TestAndLearn[F, Int](probeInterval, tryNextCosts, bestCost).map { costAdvisor =>
      for {
        cost <- costAdvisor.decision
        coins <- store.tryTake(cost)
        license <- client.issueLicense(coins: _*)
      } yield license
    }
  }
}

class TestAndLearn[F[_]: Monad, T] private (
    probeInterval: FiniteDuration,
    stateStore: Ref[F, TestAndLearn.State[F, T]],
    newProbes: T => NonEmptyList[T],
    getBest: F[T],
    makeDeferred: F[Deferred[F, T]],
    getNow: F[Long]
) {
  val decision: F[T] = {
    (getNow, makeDeferred).tupled.flatMap { case (now, deferred) =>
      stateStore.modify {
        case best @ BestDecision(cost, changedAt)
            if now <= changedAt + probeInterval.toUnit(probeInterval.unit) =>
          best -> cost.pure
        case BestDecision(previousBest, _) =>
          val newProbesComputed = newProbes(previousBest)
          Probe[F, T](newProbesComputed) -> previousBest.pure
        case Probe(costs) =>
          NonEmptyList.fromList(costs.tail) match {
            case Some(nextProbes) => Probe[F, T](nextProbes) -> costs.head.pure
            case None =>
              CalculatingBest[F, T](deferred) ->
                getBest
                  .flatTap { bestPrice =>
                    stateStore.set(BestDecision(bestPrice, now))
                  }
                  .flatMap(deferred.complete)
                  .as(costs.head)
          }
        case calculating @ CalculatingBest(computation) =>
          calculating -> computation.get
      }.flatten

    }
  }
}

object TestAndLearn {
  private sealed trait State[F[_], T]
  private case class Probe[F[_], T](costs: NonEmptyList[T]) extends State[F, T]
  private case class CalculatingBest[F[_], T](computation: Deferred[F, T]) extends State[F, T]
  private case class BestDecision[F[_], T](cost: T, changedAt: Long) extends State[F, T]

  def apply[F[_]: Concurrent: Clock, T](
      probeInterval: FiniteDuration,
      newProbes: T => NonEmptyList[T],
      getBest: F[T]
  ): F[TestAndLearn[F, T]] = {
    val getNow = Clock[F].monotonic(probeInterval.unit)
    val makeDeferred = Deferred[F, T]
    for {
      now <- getNow
      best <- getBest
      state <- Ref.of[F, State[F, T]](BestDecision(best, now))
    } yield new TestAndLearn[F, T](
      probeInterval,
      state,
      newProbes,
      getBest,
      makeDeferred,
      getNow
    )
  }
}
