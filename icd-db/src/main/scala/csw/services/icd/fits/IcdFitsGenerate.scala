package csw.services.icd.fits

import csw.services.icd.db.IcdDb
import icd.web.shared.{FitsChannel, FitsKeyInfo, FitsSource, SubsystemWithVersion}

case class IcdFitsGenerate(db: IcdDb) {

  /**
   * Generates FITS dictionary entries from the published event parameters in the
   * icd database. If a subsystem:version/component is specified, the result
   * is restricted to that subsystem:version/component.
   *
   * @param maybeSv optional subsystem:version and optional component
   * @param fitsKeyList current FITS dictionary entries
   * @return list of objects that can be merged with the existing FITS dictionary entries
   *         and then used to write the FITS-Dictionary.json file
   */
  def generate(maybeSv: Option[SubsystemWithVersion], fitsKeyList: List[FitsKeyInfo]): List[FitsKeyInfo] = {
    val subsystems = maybeSv match {
      case Some(sv) => List(sv)
      case None     => db.query.getSubsystemNames.map(SubsystemWithVersion(_, None, None))
    }
    val keyList = for {
      sv           <- subsystems
      models       <- db.versionManager.getModels(sv, includeOnly = Set("publishModel"))
      publishModel <- models.publishModel.toList
      event        <- publishModel.eventList
      param        <- event.parameterList
      keyword      <- param.keywords
    } yield {
      val source = FitsSource(
        publishModel.subsystem,
        publishModel.component,
        event.name,
        param.name,
        keyword.index,
        keyword.rowIndex
      )
      val channel = FitsChannel(source, keyword.channel.getOrElse(""))
      FitsKeyInfo(keyword.name, "", "", None, List(channel))
    }
    // merge keys with different channels into single FitsKeyInfo with list of channels
    val mergedChannelKeyList = keyList
      .groupBy(_.name)
      .map { p =>
        FitsKeyInfo(p._1, "", "", None, p._2.flatMap(_.channels))
      }
      .toList
    merge(fitsKeyList, mergedChannelKeyList)
  }

  /**
   * Merge the contents of the FITS dictionary currently in the icd db with the keyword info
   * loaded from the model files for the given subsystem[:version]/component (or all current subsystems)
   * and return a list of objects describing the new contents of the FITS dictionary.
   *
   * @param l1 list of objects from the FITS dictionary
   * @param l2 list of objects gathered from the published event parameters in the icd db
   * @return merged list of objects that can be used to write the FITS-Dictionary.json file
   */
  private def merge(l1: List[FitsKeyInfo], l2: List[FitsKeyInfo]): List[FitsKeyInfo] = {
    def mergeFitsKeyInfo(k1: FitsKeyInfo, k2: FitsKeyInfo): FitsKeyInfo = {
      val channels = k2.channels ::: k1.channels.filter(c => !k2.channels.exists(_.name == c.name))
      k1.copy(channels = channels)
    }
    val l2Map = l2.map(k => k.name -> k).toMap
    l1.map { k1 =>
      l2Map.get(k1.name) match {
        case None     => k1
        case Some(k2) => mergeFitsKeyInfo(k1, k2)
      }
    }
  }
}
