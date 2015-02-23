package csw.services.icd.gfm

/**
 * Used to manage level headings like 1.1.1, 1.1.2, etc.
 */
case class Level(level1: Int = 1, level2: Int = 1, level3: Int = 1) {
  def inc1(i: Int = 1): Level = Level(level1 + i)

  def inc2(i: Int = 1): Level = Level(level1, level2 + i)

  def inc3(i: Int = 1): Level = Level(level1, level2, level3 + i)

  def level2Str: String = s"$level1.$level2"

  def level3Str: String = s"$level1.$level2.$level3"

  def apply(i: Int): String = i match {
    case 1 => s"$level1"
    case 2 => level2Str
    case 3 => level3Str
    case _ => throw new RuntimeException("Wrong level")
  }
}
