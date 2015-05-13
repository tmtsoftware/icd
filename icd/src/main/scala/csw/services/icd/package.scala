package csw.services

import java.io.File

package object icd {

  /**
   * Gets a recursive list of subdirectories containing ICDs
   * @param dir the parent dir
   * @return
   */
  def subDirs(dir: File): List[File] = {
    val dirs = for {
      d ← dir.listFiles.filter(d ⇒ d.isDirectory && d.listFiles().contains(new File(d, "component-model.conf"))).toList
    } yield d :: subDirs(d)
    dirs.flatten
  }
}
