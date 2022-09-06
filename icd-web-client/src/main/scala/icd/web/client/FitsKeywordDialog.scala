package icd.web.client

import icd.web.client.Components.ComponentListener
import icd.web.shared.{FitsKeyInfo, FitsSource, FitsTags}
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLInputElement, document}
import org.scalajs.dom.html.Anchor
import scalatags.JsDom
import scalatags.JsDom.all._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

case class FitsKeywordDialog(fitsKeys: List[FitsKeyInfo], fitsTags: FitsTags, listener: ComponentListener) extends Displayable {

  // Action when user clicks on a component link
  private def clickedOnFitsSource(fitsSource: FitsSource)(e: dom.Event): Unit = {
    e.preventDefault()
    listener
      .componentSelected(Components.ComponentLink(fitsSource.subsystem, fitsSource.componentName))
      // Once the component is loaded, go to the parameter as well
      .foreach(_ -> Components.clickedOnFitsSource(fitsSource)(e))
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

  private def checkboxListener(e: dom.Event): Unit = {
    val checked = document.querySelectorAll(s"input[name='fitsTag']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList

    fitsKeys.map(_.name).foreach { key =>
      val elem = document.querySelector(s"#$key")
      val tags = getTags(key)
      if (checked.intersect(tags).nonEmpty)
        elem.classList.remove("d-none")
      else
        elem.classList.add("d-none")
    }
  }

  private def makeFitsTagCheckboxes() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.fitsTags)(
      label(Styles.fitsTagsLabel, strong("Tags: ")),
      fitsTags.tags.keys.toList.map(key =>
        div(cls := "form-check form-check-inline")(
          input(
            cls := "form-check-input",
            `type` := "checkbox",
            checked,
            name := s"fitsTag",
            value := key,
            onchange := checkboxListener _
          ),
          label(cls := "form-check-label", `for` := "inlineCheckbox1")(key)
        )
      )
    )
  }

  // Gets the tags for the given FITS keyword
  private def getTags(fitsKey: String): List[String] = {
    fitsTags.tags.keys.toList.flatMap { tag =>
      if (fitsTags.tags(tag).contains(fitsKey)) Some(tag) else None
    }
  }

  // Generates table with related FITS key information
  private def makeFitsKeyTable() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.component, id := "FITS-Keys")(
      makeFitsTagCheckboxes(),
      table(
        Styles.fitsTable,
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
            tr(id := info.name)(
              td(info.name),
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
