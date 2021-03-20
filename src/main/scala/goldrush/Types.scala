package goldrush

import cats.{ApplicativeError, MonadError}

object Types {

  type ThrowableMonadError[F[_]] = MonadError[F, Throwable]
  type ThrowableApplicativeError[F[_]] = ApplicativeError[F, Throwable]

}
