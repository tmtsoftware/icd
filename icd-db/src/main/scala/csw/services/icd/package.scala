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

  /**
   * Deletes the contents of the given temporary directory (recursively).
   */
  def deleteDirectoryRecursively(dir: File): Unit = {
    val tmpDir = System.getProperty("java.io.tmpdir")
    // just to be safe, don't delete anything that is not in /tmp/
    val p = dir.getPath
    if (!p.startsWith("/tmp/") && !p.startsWith(tmpDir))
      throw new RuntimeException(s"Refusing to delete $dir since not in /tmp/ or $tmpDir")

    if (dir.isDirectory) {
      dir.list.foreach { filePath =>
        val file = new File(dir, filePath)
        if (file.isDirectory) {
          deleteDirectoryRecursively(file)
        }
        else {
          file.delete()
        }
      }
      dir.delete()
    }
  }

}
