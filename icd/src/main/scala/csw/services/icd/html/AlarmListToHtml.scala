package csw.services.icd.html

import HtmlMarkup._

import icd.web.shared.IcdModels.AlarmModel

import scalatags.Text.all._

/**
 * Converts a list of alarm descriptions to HTML
 */
//noinspection TypeAnnotation
case class AlarmListToHtml(list: List[AlarmModel], compName: String) extends HtmlMarkup {


  private val titleStr = s"Alarms published by $compName"
  private val head = mkHeading(3, titleStr)

  private val body = {
    import scalatags.Text.all._
    div(this.list.map(AlarmModelToHTML(_).markup))
  }

  override val tags = if (list.nonEmpty) List(head, body) else {
    import scalatags.Text.all._
    List(div())
  }

  override val tocEntry = if (list.nonEmpty) Some(mkTocEntry(titleStr)) else None
}

//noinspection TypeAnnotation
private case class AlarmModelToHTML(m: AlarmModel) extends HtmlMarkup {

  private val head = mkHeading(4, m.name)
  private val requirements = if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", "))
  private val desc = mkParagraph(m.description)
  private val probableCause = p(strong("Probable Cause: "), raw(m.probableCause))
  private val operatorResponse = p(strong("Operator Response: "), raw(m.operatorResponse))

  private val table = mkTable(
    List("Name", "Severity Levels", "Archive", "Location", "Alarm Type", "Acknowledge", "Latched"),
    List(List(m.name, m.severityLevels.mkString(", "), yesNo(m.archive),
      m.location, m.alarmType, yesNo(m.acknowledge), yesNo(m.latched)))
  )

  override val tags = List(head, requirements, desc, probableCause, operatorResponse, table)

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(li(a(href := s"#$idStr")(m.name))))
  }
}
