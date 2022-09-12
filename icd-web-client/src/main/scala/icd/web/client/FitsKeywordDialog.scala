package icd.web.client

import icd.web.client.Components.ComponentListener
import icd.web.shared.{FitsChannel, FitsKeyInfo, FitsSource, FitsTags}
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
  private def makeLinkForFitsKeySource(fitsKey: FitsKeyInfo, fitsChannel: FitsChannel): JsDom.TypedTag[Anchor] = {
    import scalatags.JsDom.all._
    a(
      id := s"${fitsKey.name}-${fitsChannel.name}-source",
      title := s"Go to event parameter that is the source of this FITS keyword",
      s"${fitsChannel.source.toLongString} ",
      href := "#",
      onclick := clickedOnFitsSource(fitsChannel.source) _
    )
  }

  private def radioButtonListener(e: dom.Event): Unit = {
    val tag = document
      .querySelectorAll(s"input[name='fitsTag']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head

    // Set which rows are visible based on the selected tag
    fitsKeys.foreach { fitsKey =>
      val elem = document.querySelector(s"#${fitsKey.name}")
      if (tag == "All")
        elem.classList.remove("d-none")
      else {
        val tags = getTags(fitsKey)
        if (tags.contains(tag)) {
          elem.classList.remove("d-none")
          // Set which source links are visible based on the channel
          val channels = fitsKey.channels.map(_.name).filter(_.nonEmpty)
          channels.foreach { c =>
            val showSource = fitsTags.tags(tag).contains(s"${fitsKey.name}/$c")
            val sourceElem = document.querySelector(s"#${fitsKey.name}-$c-source")
            if (showSource)
              sourceElem.classList.remove("d-none")
            else
              sourceElem.classList.add("d-none")
          }
        }
        else
          elem.classList.add("d-none")
      }
    }
  }

  private def makeFitsTagPanel() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.fitsTags)(
      label(Styles.fitsTagsLabel, strong("Tags: ")),
      ("All" :: fitsTags.tags.keys.toList).map { key =>
        val displayName = key match {
          case "DL" => "Diffraction-limited"
          case "SL" => "Seeing-limited"
          case _    => key
        }
        div(cls := "form-check form-check-inline")(
          input(
            cls := "form-check-input",
            `type` := "radio",
            name := "fitsTag",
            value := key,
            id := s"fitsTag-$key",
            if (key == "All") checked else name := "fitsTag",
            onchange := radioButtonListener _
          ),
          label(cls := "form-check-label", `for` := s"fitsTag-$key")(displayName)
        )
      }
    )
  }

  // Gets the tags for the given FITS keyword
  private def getTags(fitsKey: FitsKeyInfo): List[String] = {
    fitsTags.tags.keys.toList.filter { tag =>
      fitsKey.channels.map(_.name) match {
        case List("") => fitsTags.tags(tag).contains(fitsKey.name)
        case channels => channels.exists(c => fitsTags.tags(tag).contains(s"${fitsKey.name}/$c"))
      }
    }
  }

  // Generates table with related FITS key information
  private def makeFitsKeyTable() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.component, id := "FITS-Keys")(
      makeFitsTagPanel(),
      table(
        Styles.fitsTable,
        attr("data-bs-toggle") := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Type"),
            th("Units"),
            th("Source", br, i("(component-event-param[index?])"))
          )
        ),
        tbody(
          fitsKeys.map { fitsKey =>
            // XXX TODO FIXME - add tag column if no tag selected (All tgs)
            tr(id := fitsKey.name)(
              td(fitsKey.name),
              td(raw(fitsKey.description)),
              td(fitsKey.typ),
              td(fitsKey.units),
              td(fitsKey.channels.map(c => makeLinkForFitsKeySource(fitsKey, c)))
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
