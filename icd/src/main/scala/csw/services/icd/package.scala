package csw.services

import java.io.File

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
}
