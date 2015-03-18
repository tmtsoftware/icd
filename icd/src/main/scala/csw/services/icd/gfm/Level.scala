package csw.services.icd.gfm

/**
 * Used to manage level headings like 1.1.1, 1.1.2, etc.
 */
case class Level(level1: Int = 1, level2: Int = 1, level3: Int = 1, level4: Int = 1) {
  // Return a new Level object with the given level incremented by the implicit counter
  def inc1()(implicit counter: Iterator[Int]): Level = Level(counter.next + 1)

  def inc2()(implicit counter: Iterator[Int]): Level = Level(level1, counter.next + 1)

  def inc3()(implicit counter: Iterator[Int]): Level = Level(level1, level2, counter.next + 1)

  def inc4()(implicit counter: Iterator[Int]): Level = Level(level1, level2, level3, counter.next + 1)

  // Return a new Level object with the first, second or third level incremented by the given amount
  def inc1(i: Int): Level = Level(i + 1)

  def inc2(i: Int): Level = Level(level1, i + 1)

  def inc3(i: Int): Level = Level(level1, level2, i + 1)

  def inc4(i: Int): Level = Level(level1, level2, level3, i + 1)

  // Display string for level2 header: 1.1
  def level2Str: String = s"$level1.$level2"

  // Display string for level3 header: 1.1.1
  def level3Str: String = s"$level1.$level2.$level3"

  // Display string for level4 header: 1.1.1.1
  def level4Str: String = s"$level1.$level2.$level3.$level4"

  // level(i) returns the display string for the given level
  def apply(i: Int): String = i match {
    case 1 ⇒ s"$level1"
    case 2 ⇒ level2Str
    case 3 ⇒ level3Str
    case 4 ⇒ level4Str
    case _ ⇒ throw new RuntimeException("Wrong level")
  }
}
