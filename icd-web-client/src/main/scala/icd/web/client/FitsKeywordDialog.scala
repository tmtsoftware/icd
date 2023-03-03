package icd.web.client

import icd.web.client.Components.ComponentListener
import icd.web.shared.{FitsChannel, FitsDictionary, FitsKeyInfo, FitsKeyword, FitsKeywordAndChannel, FitsSource}
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLInputElement, document}
import scalatags.JsDom.all._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

object FitsKeywordDialog {
  private val allTags = "All"

  private val fitsTagNameCls = "fitsTagName"

  private def hideElement(e: Element): Unit = {
    e.classList.add("d-none")
  }

  private def showElement(e: Element): Unit = {
    e.classList.remove("d-none")
  }

  /**
   * Returns the selected FITS tag (or "All")
   */
  def getFitsTag: String = {
    document
      .querySelectorAll(s"input[name='fitsTag']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
  }
}

case class FitsKeywordDialog(fitsDict: FitsDictionary, listener: ComponentListener) extends Displayable {
  import FitsKeywordDialog._

  private val fitsTags = fitsDict.fitsTags
  private val fitsKeys = fitsDict.fitsKeys

  // Map from FITS keyword and channel to the tag for that keyword/channel
  private val tagMap = fitsDict.fitsTags.tags.view.values
    .flatMap(_.map(f => (FitsKeywordAndChannel(f.keyword, f.channel), f.tag)))
    .toMap

  // Action when user clicks on a component link
  private def clickedOnFitsSource(fitsSource: FitsSource)(e: dom.Event): Unit = {
    e.preventDefault()
    listener
      .componentSelected(Components.ComponentLink(fitsSource.subsystem, fitsSource.componentName))
      // Once the component is loaded, go to the parameter as well
      .foreach(_ -> Components.clickedOnFitsSource(fitsSource)(e))
  }

  // Makes the link for a FITS keyword source to the event that is the source of the keyword
  private def makeLinkForFitsKeySource(fitsKey: FitsKeyInfo, fitsChannel: FitsChannel, index: Int) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val channel  = if (fitsChannel.name.isEmpty) None else Some(fitsChannel.name)
    val maybeTag = tagMap.get(FitsKeywordAndChannel(fitsKey.name, channel))

    div(
      if (index != 0) hr(cls := fitsTagNameCls) else span(),
      id := s"${fitsKey.name}-${fitsChannel.name}-source",
      // Show the tag name
      maybeTag.map(tag => span(cls := fitsTagNameCls, tag, ": ")),
      a(
        title := s"Go to event parameter that is the source of this FITS keyword",
        s"${fitsChannel.source.toLongString} ",
        href := "#",
        onclick := clickedOnFitsSource(fitsChannel.source) _
      )
    )
  }

  //noinspection ScalaUnusedSymbol
  private def radioButtonListener(e: dom.Event): Unit = {
    val tag = getFitsTag

    // Set which rows are visible based on the selected tag
    fitsKeys.foreach { fitsKey =>
      val elem = document.querySelector(s"#${fitsKey.name}")
      if (tag == allTags) {
        showElement(elem)
        val channels = fitsKey.channels.map(_.name).filter(_.nonEmpty)
        channels.foreach { c =>
          val sourceElem = document.querySelector(s"#${fitsKey.name}-$c-source")
          showElement(sourceElem)
        }
      }
      else {
        val tags = getTags(fitsKey)
        if (tags.contains(tag)) {
          showElement(elem)
          // Set which source links are visible based on the channel
          val channels = fitsKey.channels.map(_.name).filter(_.nonEmpty)
          channels.foreach { c =>
            val showSource = fitsTags.tags(tag).contains(FitsKeyword(fitsKey.name, tag, Some(c)))
            val sourceElem = document.querySelector(s"#${fitsKey.name}-$c-source")
            if (showSource)
              showElement(sourceElem)
            else
              hideElement(sourceElem)
          }
        }
        else
          hideElement(elem)
      }
    }
  }

  private def makeFitsTagPanel() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.fitsTags)(
      label(Styles.fitsTagsLabel, strong("Tags: ")),
      (allTags :: fitsTags.tags.keys.toList).map { key =>
        val displayName = key match {
          case "DL" => "Diffraction-limited (DL)"
          case "SL" => "Seeing-limited (SL)"
          case _    => key
        }
        div(cls := "form-check form-check-inline")(
          input(
            cls := "form-check-input",
            `type` := "radio",
            name := "fitsTag",
            value := key,
            id := s"fitsTag-$key",
            if (key == allTags) checked else name := "fitsTag",
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
        case List("") =>
          fitsTags.tags(tag).exists(_.keyword == fitsKey.name)
        case channels =>
          channels.exists(c =>
            fitsTags
              .tags(tag)
              .contains(FitsKeyword(fitsKey.name, tag, Some(c)))
          )
      }
    }
  }

  // Generates table with related FITS key information
  private def makeFitsKeyTable() = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    def makeFitsTableRows() = {
      fitsKeys.map { fitsKey =>
        val iList = fitsKey.channels.indices.toList
        val zList = fitsKey.channels.zip(iList)
        tr(id := fitsKey.name)(
          td(fitsKey.name),
          td(raw(fitsKey.description)),
          td(fitsKey.typ),
          td(fitsKey.units),
          td(zList.map(p => makeLinkForFitsKeySource(fitsKey, p._1, p._2)))
        )
      }
    }

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
            th(span(cls := fitsTagNameCls, "Tag: "), a(href := "#")("Source"))
          )
        ),
        tbody(
          makeFitsTableRows()
        )
      )
    )
  }

  def markup(): Element = {
    if (fitsKeys.isEmpty || fitsTags.tags.isEmpty)
      div(
        p(
          "Missing FITS Dictionary data: May need to manually upload/ingest ",
          a(href := "https://github.com/tmt-icd/DMS-Model-Files")("DMS-Model-Files")
        )
      ).render
    else
      div(makeFitsKeyTable()).render
  }

}
