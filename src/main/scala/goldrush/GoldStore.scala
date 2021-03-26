package goldrush

import cats.Monad
import cats.data.Chain
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift}
import cats.syntax.flatMap._
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

class GoldStoreImpl[F[_]: Monad] private (store: Ref[F, Chain[Coin]]) extends GoldStore[F] {
  override def put(coins: Coin*): F[Unit] = {
    val chainedCoins = Chain.fromSeq(coins)
    store.update(_ ++ chainedCoins)
  }

  override def tryTake(coinsNumber: Coin): F[Seq[Coin]] = {
    store.get.map(_.toList)
  }
}

object GoldStoreImpl {
  def apply[F[_]: Concurrent: ContextShift](storageSize: Int): F[GoldStore[F]] = {
    Ref.of(Chain.empty[Coin]).map { store =>
      new GoldStoreImpl[F](store)
    }
  }
}
