package icd.web.client

import icd.web.shared.{FitsKeyInfo, FitsSource}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.Anchor
import scalatags.JsDom
import scalatags.JsDom.all._

case class FitsKeywordDialog(fitsKeys: List[FitsKeyInfo]) extends Displayable {

  // Action when user clicks on a component link
  private def clickedOnFitsSource(fitsSource: FitsSource)(e: dom.Event): Unit = {
    e.preventDefault()
//    val idStr = Headings.idFor(
//      fitsSource.componentName,
//      "publishes",
//      "Event",
//      fitsSource.subsystem,
//      fitsSource.componentName,
//      fitsSource.eventName
//    )
//    val hiddenRowId = makeHiddenRowId(idStr)
//    document.getElementById(hiddenRowId).classList.remove("collapse")
//    val paramId = s"$idStr.${fitsSource.parameterName}"
//    document.getElementById(paramId).scrollIntoView()
  }

  // Makes the link for a FITS keyword source to the event that is the source of the keyword
  private def makeLinkForFitsKeySource(fitsSource: FitsSource): JsDom.TypedTag[Anchor] = {
    import scalatags.JsDom.all._
    a(
      title := s"Go to event parameter that is the source of this FITS keyword",
      s"${fitsSource.toLongString} ",
      href := "#",
      onclick := clickedOnFitsSource(fitsSource) _
    )
  }

  // Generates table with related FITS key information
  private def makeFitsKeyTable() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.component, id := "FITS-Keys")(
      table(
        Styles.componentTable,
        attr("data-bs-toggle") := "table",
        thead(
          tr(
            th("Name"),
            th("Title"),
            th("Description"),
            th("Type"),
            th("Default"),
            th("Units"),
            th("Source", br, i("(component-event-param[index?])")),
            th("Note")
          )
        ),
        tbody(
          fitsKeys.map { info =>
            tr(
              td(a(id := info.name, name := info.name)(info.name)),
              td(info.title),
              td(raw(info.description)),
              td(info.typ),
              td(info.defaultValue),
              td(info.units),
              td(info.source.map(makeLinkForFitsKeySource)),
              td(info.note)
            )
          }
        )
      )
    )
  }

  def markup(): Element = {
    div(makeFitsKeyTable()).render
  }

}
