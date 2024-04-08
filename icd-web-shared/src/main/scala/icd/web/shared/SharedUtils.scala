package icd.web.shared

/**
 * Common, shared utility code
 */
object SharedUtils {


  /**
   * Holds the credentials needed to use the web app
   *
   * @param user  user name
   * @param password  password
   */
  case class Credentials(user: String, password: String) {
    override def toString: String = s"$user:$password"
  }

  /**
   * Removes any columns that do not contain any values
   *
   * @param head table headings
   * @param rows table rows
   * @return the input, minus any empty columns
   */
  def compact(head: List[String], rows: List[List[String]]): (List[String], List[List[String]]) = {
    def notAllEmpty(rows: List[List[String]], i: Int): Boolean = {
      val l = for (r <- rows) yield r(i).length
      l.sum != 0
    }

    val hh = for {
      (h, i) <- head.zipWithIndex
      if notAllEmpty(rows, i)
    } yield (h, i)
    if (hh.length == head.length) {
      (head, rows)
    } else {
      val newHead = hh.map(_._1)
      val indexes = hh.map(_._2)

      def newRow(row: List[String]): List[String] = {
        row.zipWithIndex.filter(p => indexes.contains(p._2)).map(_._1)
      }

      val newRows = rows.map(newRow)
      (newHead, newRows)
    }
  }

  def showComponentInfo(info: ComponentInfo): Boolean = {
    info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
      || info.commands.get.commandsSent.nonEmpty)
      || info.services.isDefined && (info.services.get.servicesProvided.nonEmpty || info.services.get.servicesRequired.nonEmpty)
  }

//  // Inverts a map of T to List[T]
//  implicit class MapInverter[T, U](map: Map[T, List[U]]) {
//    def invert: Map[U, T] = {
//      val result = collection.mutable.Map.empty[U, T]
//
//      for ((key, values) <- map) {
//        for (v <- values) {
//          result += (v -> key)
//        }
//      }
//      result.toMap
//    }
//  }
}
