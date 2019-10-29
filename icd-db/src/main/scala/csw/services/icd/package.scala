package csw.services

import java.io.File
import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}

package object icd {

  /**
   * Gets a recursive list of subdirectories containing ICD files
   *
   * @param dir the parent dir
   * @return
   */
  def subDirs(dir: File): List[File] = {
    val dirs = for {
      d <- dir.listFiles.filter(StdName.isStdDir).toList
    } yield d :: subDirs(d)
    dirs.flatten
  }

  /**
  * Shortcut for Await.result
   */
  implicit class RichFuture[T](val f: Future[T]) extends AnyVal {
    def await: T = Await.result(f, 100.seconds)
  }
}
