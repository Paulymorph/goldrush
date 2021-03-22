package goldrush

import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.Future

trait MonixSpec extends BeforeAndAfterAll {
  this: Suite =>
  protected implicit val s: SchedulerService = Scheduler.computation(name = "test")

  protected implicit def taskToFuture[A](computation: Task[A]): Future[A] = computation.runToFuture

  override def afterAll() = {
    s.shutdown()
  }
}
