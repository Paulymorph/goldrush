package goldrush

import cats.Applicative
import cats.effect.{Concurrent, ContextShift}
import cats.syntax.functor._
import cats.syntax.traverse._
import goldrush.GoldStore.Coin
import monix.catnap.ConcurrentQueue

trait GoldStore[F[_]] {
  def put(coins: Coin*): F[Unit]
  def tryTake(coinsNumber: Int): F[Seq[Coin]]
}

object GoldStore {
  type Coin = Int
}

class GoldStoreImpl[F[_]: Applicative] private (store: ConcurrentQueue[F, Coin])
    extends GoldStore[F] {
  override def put(coins: Coin*): F[Unit] = {
    coins.traverse(store.tryOffer).void
  }

  override def tryTake(coinsNumber: Coin): F[Seq[Coin]] = {
    Seq.fill(coinsNumber)(coinsNumber).flatTraverse { _ =>
      store.tryPoll.map(_.toSeq)
    }
  }
}

object GoldStoreImpl {
  def apply[F[_]: Concurrent: ContextShift](storageSize: Int): F[GoldStore[F]] = {
    ConcurrentQueue.bounded[F, Coin](storageSize).map { store =>
      new GoldStoreImpl[F](store)
    }
  }
}
