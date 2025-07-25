package icd.web.client

import icd.web.shared.ComponentInfo.*
import icd.web.shared.IcdModels.*
import icd.web.shared.*
import org.scalajs.dom
import org.scalajs.dom.{HTMLButtonElement, HTMLDivElement, HTMLElement, HTMLTableRowElement, document}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.Future
import Components.*
import org.scalajs.dom.html.{Anchor, Div, Element}
import play.api.libs.json.*

import scala.util.Failure
import scalatags.JsDom.TypedTag
import Headings.idFor
import icd.web.shared.TitleInfo.unpublished

object Components {

  // Id of component info for given component name
  def getComponentInfoId(compName: String): String = compName

  /**
   * Information about a link to a component
   *
   * @param subsystem the component's subsystem
   * @param compName  the component name
   */
  case class ComponentLink(subsystem: String, compName: String)

  trait ComponentListener {

    /**
     * Called when a link for the component is clicked
     *
     * @param link contains the component's subsystem and name
     */
    def componentSelected(link: ComponentLink): Future[Unit]
  }

  def yesNo(b: Boolean): String = if (b) "yes" else "no"

  /**
   * Returns a HTML table with the given column headings and list of rows
   *
   * @param headings   the table headings
   * @param rowList    list of row data
   * @param tableClass optional table class for css
   * @return an html table element
   */
  def mkTable(
      headings: List[String],
      rowList: List[List[String]],
      tableClass: String = ""
  ): TypedTag[HTMLElement] = {
    import scalatags.JsDom.all.*

    // Returns a table cell markup, checking if the text is already in html format (after markdown processing)
    def mkTableCell(text: String) = {
      if (text.startsWith("<"))
        td(raw(text))
      else
        td(p(text))
    }

    if (rowList.isEmpty) div()
    else {
      val (newHead, newRows) = SharedUtils.compact(headings, rowList)
      if (newHead.isEmpty) div()
      else {
        table(
          cls                    := tableClass,
          attr("data-bs-toggle") := "table",
          thead(
            tr(newHead.map(th(_)))
          ),
          tbody(
            for (row <- newRows) yield {
              tr(row.map(mkTableCell))
            }
          )
        )
      }
    }
  }

  // Returns an element id to use for the detail row that is normally hidden until you click on the toggle.
  // For some reason using the string id did not work, so using the hash here.
  private def makeHiddenRowId(id: String) = s"hiddenRow-${id.##}"

  // Action when user clicks on a component link
  def clickedOnFitsSource(fitsSource: FitsSource)(e: dom.Event): Unit = {
    e.preventDefault()
    val idStr = Headings.idFor(
      fitsSource.componentName,
      "publishes",
      "Event",
      fitsSource.subsystem,
      fitsSource.componentName,
      fitsSource.eventName
    )
    val hiddenRowId   = makeHiddenRowId(idStr)
    val hiddenRowElem = document.getElementById(hiddenRowId)
    if (hiddenRowElem != null) {
      // Open up the hidden parameter row, so that the parameters are displayed
      hiddenRowElem.classList.remove("collapse")
      val paramId   = s"$idStr.${fitsSource.parameterName}"
      val paramElem = document.getElementById(paramId)
      if (paramElem != null)
        paramElem.scrollIntoView()
    }
  }
}

/**
 * Manages the component (Assembly, HCD) display
 *
 * @param mainContent used to display information about selected components
 * @param listener    called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
//noinspection DuplicatedCode,SameParameterValue
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components.*
  import icd.web.shared.JsonSupport.*

  // Action when user clicks on a component link
  private def clickedOnComponent(subsystem: String, component: String)(e: dom.Event): Unit = {
    e.preventDefault()
    listener.componentSelected(ComponentLink(subsystem, component))
  }

  // Makes the link for a component in the table
  private def makeLinkForComponent(subsystem: String, component: String): TypedTag[Anchor] = {
    import scalatags.JsDom.all.*
    a(
      title := s"Show API for $subsystem.$component",
      s"$subsystem.$component ",
      href    := "#",
      onclick := clickedOnComponent(subsystem, component)
    )
  }

  // Makes the link for a component in the table
  private def makeLinkForComponent(componentModel: ComponentModel): TypedTag[Anchor] = {
    makeLinkForComponent(componentModel.subsystem, componentModel.component)
  }

  // Makes the link for a FITS keyword source to the event that is the source of the keyword
  private def makeLinkForFitsKeySource(fitsChannel: FitsChannel, index: Int) = {
    import scalatags.JsDom.all.*
    val fitsSource = fitsChannel.source
    div(
      if (index != 0) hr else span(),
      a(
        title := s"Go to event parameter that is the source of this FITS keyword",
        s"${fitsSource.toShortString} ",
        href    := "#",
        onclick := clickedOnFitsSource(fitsSource)
      )
    )
  }

  /**
   * Gets information about the given components
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional target subsystem and version
   * @param searchAllSubsystems if true search all TMT subsystems for API dependencies
   * @param clientApi if true include subscribed events, sent commands in API
   * @return future list of objects describing the components
   */
  private def getComponentInfo(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    Fetch
      .get(ClientRoutes.icdComponentInfo(sv, maybeTargetSv, searchAllSubsystems, clientApi))
      .map { jsonStr =>
        Json.fromJson[Array[ComponentInfo]](Json.parse(jsonStr)).map(_.toList).getOrElse(Nil)
      }
  }

  /**
   * Gets an Archived Items Report in HTML
   *
   * @param sv            the subsystem
   * @return future HTML string for the report
   */
  private def getArchivedItemsReportHtml(sv: SubsystemWithVersion): Future[String] = {
    Fetch.get(ClientRoutes.archivedItemsReportHtml(sv))
  }

  /**
   * Gets a Missing Items Report in HTML
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional other subsystem
   * @return future HTML string for the report
   */
  private def getMissingItemsReportHtml(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Future[String] = {
    Fetch.get(ClientRoutes.missingItemsReportHtml(sv, maybeTargetSv))
  }

  /**
   * Gets a relationship graph for the given subsystems
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional other subsystem
   * @return future HTML string URL for the graph image
   */
  private def getGraphUrl(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      icdVersion: Option[String]
  ): Future[String] = {
    import org.scalajs.dom.URL
    val options = IcdVizOptions(
      imageFormat = "PNG",
      commandLabels = true,
      missingEvents = false
    )
    for {
      blob <- Fetch.getBlob(ClientRoutes.makeGraph(sv, maybeTargetSv, icdVersion, options))
    } yield {
      URL.createObjectURL(blob)
    }
  }

  /**
   * Gets the list of components for the given subsystem and then gets the information for them
   */
  private def getComponentInfo(
      maybeSubsystem: Option[SubsystemWithVersion],
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    maybeSubsystem match {
      case None =>
        Future.successful(Nil)
      case Some(sv) =>
        getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems, clientApi)
    }
  }

  // Gets top level subsystem info from the server
  private def getSubsystemInfo(sv: SubsystemWithVersion): Future[SubsystemInfo] = {
    val path = ClientRoutes.subsystemInfo(sv.subsystem, sv.maybeVersion, sv.maybeComponent)
    Fetch.get(path).map { text =>
      val subsystemInfo = Json.fromJson[SubsystemInfo](Json.parse(text)).get
      subsystemInfo.copy(sv = sv) // include the component, if specified
    }
  }

  /**
   * Gets a list of FITS keywords for a given subsystem/component
   *
   * @param sv the subsystem/component
   * @return future list of objects describing the FITS keys whose source is an event published by the subsystem
   */
  private def getFitsDictionary(sv: SubsystemWithVersion): Future[FitsDictionary] = {
    Fetch
      .get(ClientRoutes.fitsDictionary(Some(sv)))
      .map { text =>
        Json.fromJson[FitsDictionary](Json.parse(text)).get
      }
  }

  // Gets additional information about the ICD between the two subsystems
  private def getIcdModelList(sv: SubsystemWithVersion, tv: SubsystemWithVersion): Future[List[IcdModel]] = {
    val path = ClientRoutes.icdModelList(sv, tv)
    Fetch.get(path).map { text =>
      Json.fromJson[List[IcdModel]](Json.parse(text)).get
    }
  }

  /**
   * Adds components from two subsystems to the display (for an ICD).
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param targetSv             target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   * @param searchAllSubsystems  if true, search all subsystems for API dependencies
   * @param clientApi            if true include subscribed events and sent commands in API
   * @return a future list of ComponentInfo (one entry for each component in the result)
   */
  def addComponentsForIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybeIcd: Option[IcdVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    val f = for {
      subsystemInfo       <- getSubsystemInfo(sv)
      targetSubsystemInfo <- getSubsystemInfo(targetSv)
      icdInfoList         <- getIcdModelList(sv, targetSv)
      infoList            <- getComponentInfo(sv, Some(targetSv), searchAllSubsystems, clientApi)
      targetInfoList      <- getComponentInfo(targetSv, Some(sv), searchAllSubsystems, clientApi)
    } yield {
      import scalatags.JsDom.all.*

      val titleInfo              = TitleInfo(subsystemInfo, Some(targetSv), maybeIcd)
      val subsystemVersion       = sv.maybeVersion.getOrElse(TitleInfo.unpublished)
      val targetSubsystemVersion = targetSv.maybeVersion.getOrElse(TitleInfo.unpublished)
      mainContent.clearContent()
      mainContent.setTitle(titleInfo.title, titleInfo.maybeSubtitle, titleInfo.maybeDescription)
      mainContent.appendElement(
        div(
          cls := "component container-fluid",
          p(strong(s"${subsystemInfo.sv.subsystem}: ${subsystemInfo.title} $subsystemVersion")),
          raw(subsystemInfo.description),
          if (subsystemInfo.sv == targetSubsystemInfo.sv) div()
          else
            div(
              p(strong(s"${targetSubsystemInfo.sv.subsystem}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
              raw(targetSubsystemInfo.description)
            ),
          icdInfoList.map(i => div(p(strong(i.titleStr)), raw(i.description)))
        ).render
      )
      // XXX TODO FIXME: Hyperlinks to other subsystems can't be made in the summary table,
      // since the code is shared with non-javascript code on the server side.
      val summaryTable1 =
        SummaryTable(
          subsystemInfo,
          Some(targetSv),
          infoList,
          new HtmlHeadings,
          clientApi = false,
          displayTitle = true
        ).displaySummary().render
      val summaryTable2 =
        if (subsystemInfo.sv != targetSubsystemInfo.sv)
          Some(
            SummaryTable(
              targetSubsystemInfo,
              Some(sv),
              targetInfoList,
              new HtmlHeadings,
              clientApi = false,
              displayTitle = false
            ).displaySummary().render
          )
        else None

      mainContent.appendElement(
        div(cls := "component container-fluid", id := "Summary")(
          raw(summaryTable1),
          summaryTable2.map(raw).getOrElse(span())
        ).render
      )
      infoList.foreach(i => displayComponentInfo(i, forApi = false, clientApi = clientApi))
      if (subsystemInfo.sv != targetSubsystemInfo.sv)
        targetInfoList.foreach(i => displayComponentInfo(i, forApi = false, clientApi))
      infoList ++ targetInfoList
    }

    f.onComplete {
      case Failure(ex) => mainContent.displayInternalError(ex)
      case _           =>
    }
    f
  }

  /**
   * Adds components for an ICD with DMS (Just a header and Archived Items table).
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param targetSv             target subsystem, version, optional component
   */
  def addComponentsForDmsIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion
  ): Future[Unit] = {
    if (sv.subsystem == "DMS" && targetSv.subsystem != "DMS" || targetSv.subsystem == "DMS" && sv.subsystem != "DMS") {
      // Special case: When DMS is involved, ICD consists of "Archived Items Report" with an ICD header
      // page (DEOPSICDDB-138)
      val sv2 = if (sv.subsystem == "DMS") targetSv else sv

      val f = for {
        archiveReportHtml <- getArchivedItemsReportHtml(sv2)
      } yield {
        import scalatags.JsDom.all.*
        mainContent.appendElement(
          div(
            cls := "component container-fluid",
            raw(archiveReportHtml)
          ).render
        )
      }
      f.onComplete {
        case Failure(ex) => mainContent.displayInternalError(ex)
        case _           =>
      }
      f
    }
    else Future.successful(())
  }

  /**
   * Adds a missing items report for ICDs
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param targetSv             target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   */
  def addMissingItemsReportForIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybeIcd: Option[IcdVersion]
  ): Future[Unit] = {
    val f = for {
      missingItemsReportHtml <- getMissingItemsReportHtml(sv, Some(targetSv))
    } yield {
      import scalatags.JsDom.all.*
      if (mainContent.getTitle.startsWith("ICD")) {
        mainContent.appendElement(
          div(
            cls := "component container-fluid",
            raw(missingItemsReportHtml)
          ).render
        )
      }
    }
    f.onComplete {
      case Failure(ex) => mainContent.displayInternalError(ex)
      case _           =>
    }
    f
  }

  /**
   * Adds a relationship graph for ICDs
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param targetSv             target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   */
  def addGraphForIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybeIcd: Option[IcdVersion]
  ): Future[Unit] = {
    val f = for {
      graphUrl <- getGraphUrl(sv, Some(targetSv), maybeIcd.map(_.icdVersion))
    } yield {
      import scalatags.JsDom.all.*
      // Should only do this if the user did not switch to a different view before it completed
      if (mainContent.getTitle.startsWith("ICD")) {
        mainContent.appendElement(
          div(
            cls := "component container-fluid",
            h2(a(name := "graph")(s"Graph showing connections between $sv and $targetSv")),
            img(src := graphUrl, maxWidth := "100%")
          ).render
        )
      }
    }
    f.onComplete {
      case Failure(ex) => // mainContent.displayInternalError(ex)
      case _           =>
    }
    f
  }

  /**
   * Adds components to the display.
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param maybeTargetSubsystem optional target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   * @param searchAllSubsystems  if true, search all subsystems for API dependencies
   * @param clientApi            if true include subscribed events and sent commands in API
   * @return a future list of ComponentInfo (one entry for each component in the result)
   */
  def addComponents(
      sv: SubsystemWithVersion,
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    import scalatags.JsDom.all.*

    if (maybeTargetSubsystem.isDefined) {
      val targetSv = maybeTargetSubsystem.get
      for {
        compInfoList <- addComponentsForIcd(sv, targetSv, maybeIcd, searchAllSubsystems, clientApi)
        _            <- addComponentsForDmsIcd(sv, targetSv)
        _            <- addMissingItemsReportForIcd(sv, targetSv, maybeIcd)
        _            <- addGraphForIcd(sv, targetSv, maybeIcd)
      } yield compInfoList
    }
    else {
      val f = for {
        subsystemInfo <- getSubsystemInfo(sv)
        infoList      <- getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems, clientApi)
        fitsDict      <- getFitsDictionary(sv)
      } yield {
        val titleInfo = TitleInfo(subsystemInfo, maybeTargetSubsystem, maybeIcd)
        mainContent.clearContent()
        mainContent.setTitle(titleInfo.title, titleInfo.maybeSubtitle, titleInfo.maybeDescription)
        // XXX TODO FIXME: Hyperlinks to other subsystems can't be made in the summary table,
        // since the code is shared with non-javascript code on the server side.
        val summaryTable =
          SummaryTable(
            subsystemInfo,
            maybeTargetSubsystem,
            infoList,
            new HtmlHeadings,
            clientApi,
            displayTitle = true
          ).displaySummary().render

        mainContent.appendElement(
          div(cls := "component container-fluid", id := "Summary")(raw(summaryTable)).render
        )
        if (fitsDict.fitsKeys.nonEmpty) mainContent.appendElement(makeFitsKeyTable(fitsDict, sv).render)
        infoList.foreach(i => displayComponentInfo(i, forApi = true, clientApi = clientApi))
        infoList
      }
      f.onComplete {
        case Failure(ex) => mainContent.displayInternalError(ex)
        case _           =>
      }
      f
    }
  }

  /**
   * Displays the information for a component, appending to the other selected components, if any.
   *
   * @param info contains the information to display
   */
  private def displayComponentInfo(
      info: ComponentInfo,
      forApi: Boolean,
      clientApi: Boolean
  ): Unit = {
    if (forApi || SharedUtils.showComponentInfo(info)) {
      val markup     = markupForComponent(info, forApi, clientApi).render
      val oldElement = $id(getComponentInfoId(info.componentModel.component))
      if (oldElement == null) {
        mainContent.appendElement(markup)
      }
      else {
        // Use existing div, so the component's position stays the same
        mainContent.replaceElement(oldElement, markup)
      }
    }
  }

  /**
   * Returns a table of attributes (parameters for an event)
   *
   * @param parameterList list of attributes to display
   * @return
   */
  private def eventParameterListMarkup(
      parameterList: List[ParameterModel],
      forApi: Boolean,
      maybeEventId: Option[String] = None
  ): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "FITS Keywords")
      val rowList =
        for (a <- parameterList) yield {
          val paramId    = maybeEventId.map(s => s"$s.${a.name}")
          val nameAnchor = paramId.map(p => s"<a id='$p' name='$p'>${a.name}</a>").getOrElse(a.name)
          // For now, only including FITS keyword table in APIs, not ICDs
          val fitsKeywordLinks =
            if (forApi)
              a.getFitsKeys.map(k => s"<a href=#$k>$k</a>").mkString(", ")
            else a.getFitsKeys.mkString(", ")
          List(nameAnchor, a.description, a.typeStr, a.units, a.defaultValue, fitsKeywordLinks)
        }
      div(
        strong("Parameters"),
        mkTable(headings, rowList, "attributeTable"),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  /**
   * Returns a table of image metadata
   */
  private def imageMetadataListMarkup(
      nameStr: String,
      metadataList: List[MetadataModel],
      maybeImageId: Option[String] = None
  ): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all.*
    if (metadataList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Keyword")
      val rowList =
        for (a <- metadataList) yield {
          val imageId    = maybeImageId.map(s => s"$s.${a.name}")
          val nameAnchor = imageId.map(p => s"<a id='$p' name='$p'>${a.name}</a>").getOrElse(a.name)
          List(nameAnchor, a.description, a.dataType, a.keyword)
        }
      div(
        strong(s"Image Metadata for $nameStr"),
        mkTable(headings, rowList, "attributeTable")
      )
    }
  }

  /**
   * Returns a table of parameters (for commands)
   *
   * @param parameterList list of attributes to display
   * @param requiredArgs   a list of required arguments
   */
  private def commandParameterListMarkup(
      parameterList: List[ParameterModel],
      requiredArgs: List[String]
  ): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList =
        for (a <- parameterList)
          yield List(
            a.name,
            a.description,
            a.typeStr,
            a.units,
            a.defaultValue,
            yesNo(requiredArgs.contains(a.name))
          )
      div(
        strong("Parameters"),
        mkTable(headings, rowList, "attributeTable"),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  /**
   * Returns a table listing the attributes of a command result
   *
   * @param parameterList list of attributes to display
   */
  private def resultTypeMarkup(parameterList: List[ParameterModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- parameterList) yield List(a.name, a.description, a.typeStr, a.units)
      div(
        strong("Command Result Parameters"),
        mkTable(headings, rowList, "attributeTable"),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  private def resultMarkup(maybeResult: Option[CommandResultModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all.*
    if (maybeResult.isEmpty) div()
    else {
      val result = maybeResult.get
      div(cls := "nopagebreak")(
        if (result.description.nonEmpty)
          div(p(strong("Command Results")), raw(result.description))
        else
          div(),
        resultTypeMarkup(result.parameters)
      )
    }
  }

  /**
   * Returns a hidden, expandable table row containing the given div item
   *
   * @param targetId HTML id for target item (modified for toggle button and used to expand row for FITS key links)
   * @param item    the contents of the table row
   * @param colSpan the number of columns to span
   * @return a pair of (button, tr) elements, where the button toggles the visibility of the row
   */
  private def hiddenRowMarkup(
      targetId: String,
      item: TypedTag[HTMLDivElement],
      colSpan: Int
  ): (TypedTag[HTMLButtonElement], TypedTag[HTMLTableRowElement]) = {
    import scalatags.JsDom.all.*

    // button to toggle visibility
    val rowId    = makeHiddenRowId(targetId)
    val buttonId = s"button-$targetId"
    val btn = button(
      cls                    := "attributeBtn btn btn-sm",
      `type`                 := "button",
      id                     := buttonId,
      name                   := buttonId,
      attr("data-bs-toggle") := "collapse",
      attr("data-bs-target") := s"#$rowId",
      title                  := "Show/hide details"
    )(
      i(cls := "bi bi-caret-down-square")
    )
    val row = tr(id := rowId, cls := "collapse panel-collapse")(td(colspan := colSpan)(item))
    (btn, row)
  }

  private def formatRate(maybeRate: Option[Double]) = {
    import scalatags.JsDom.all.*
    val (maxRate, defaultMaxRateUsed) = EventModel.getMaxRate(maybeRate)
    val el                            = if (defaultMaxRateUsed) em(s"$maxRate Hz *") else span(s"$maxRate Hz")
    el.render.outerHTML
  }

  private def makeErrorDiv(msg: String): TypedTag[Div] = {
    import scalatags.JsDom.all.*
    div(cls := "alert alert-warning", role := "alert")(
      span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
      span(em(s" $msg"))
    )
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(component: ComponentModel, maybePublishes: Option[Publishes], forApi: Boolean, clientApi: Boolean) = {
    import scalatags.JsDom.all.*

    val compName = component.component

    // Returns a div displaying more details for the given event
    def makeEventDetailsRow(eventInfo: EventInfo, pubType: String, maybeEventId: Option[String] = None) = {
      val showArchiveInfo = pubType != "Observe Events"
      val eventModel      = eventInfo.eventModel
      val totalArchiveSpacePerYear =
        if (eventModel.totalArchiveSpacePerYear.isEmpty) ""
        else if (eventModel.maybeMaxRate.isEmpty) em(eventModel.totalArchiveSpacePerYear).render.outerHTML
        else span(eventModel.totalArchiveSpacePerYear).render.outerHTML
      val headings = List("Max Rate", "Archive", "Archive Duration", "Bytes per Event", "Year Accumulation")
      val rowList =
        if (showArchiveInfo)
          List(
            List(
              formatRate(eventModel.maybeMaxRate),
              yesNo(eventModel.archive),
              eventModel.archiveDuration,
              eventModel.totalSizeInBytes.toString,
              totalArchiveSpacePerYear
            )
          )
        else Nil

      div(
        if (eventModel.refError.startsWith("Error:")) makeErrorDiv(eventModel.refError) else div(),
        if (eventModel.requirements.isEmpty) div()
        else p(strong("Requirements: "), eventModel.requirements.mkString(", ")),
        if (showArchiveInfo) mkTable(headings, rowList) else div(),
        if (showArchiveInfo && eventModel.maybeMaxRate.isEmpty) span("* Default maxRate of 1 Hz assumed.") else span(),
        eventParameterListMarkup(eventModel.parameterList, forApi, maybeEventId),
        if (pubType == "Events") p(strong("Category: "), eventModel.getCategory) else span()
      )
    }

    // Returns a div displaying more details for the given image
    def makeImageDetailsRow(imageInfo: ImageInfo, maybeImageId: Option[String] = None) = {
      val imageModel = imageInfo.imageModel
      val imageSize = imageModel.size match {
        case (0, 0) => ""
        case (w, h) => s"$w x $h"
      }
      val headings =
        List("Channel", "Format", "Size", "Pixel Size", "Max Rate")
      val rowList =
        List(
          List(
            imageModel.channel,
            imageModel.format,
            imageSize,
            imageModel.pixelSize.toString,
            formatRate(imageModel.maybeMaxRate)
          )
        )

      div(
        mkTable(headings, rowList),
        imageMetadataListMarkup(imageModel.name, imageModel.metadataList, maybeImageId)
      )
    }

    // Returns the markup for the published event
    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]) = {
      if (eventList.isEmpty) div()
      else
        div(
          h3(s"$pubType Published by $compName"),
          table(
            attr("data-bs-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description"),
                if (clientApi) th("Subscribers") else span
              )
            ),
            tbody(
              for (t <- eventList) yield {
                val idStr      = idFor(compName, "publishes", pubType, component.subsystem, compName, t.eventModel.name)
                val (btn, row) = hiddenRowMarkup(idStr, makeEventDetailsRow(t, pubType, Some(idStr)), 3)
                List(
                  tr(
                    td(
                      cls := "attributeCell",
                      p(
                        btn,
                        a(id := idStr, name := idStr)(
                          t.eventModel.name
                        )
                      )
                    ),
                    td(raw(t.eventModel.description)),
                    if (clientApi)
                      td(p(t.subscribers.map(_.componentModel).distinct.map(makeLinkForComponent)))
                    else span
                  ),
                  row
                )
              }
            )
          )
        )
    }

    // Returns the markup for the published image
    def publishImageListMarkup(pubType: String, imageList: List[ImageInfo]) = {
      if (imageList.isEmpty) div()
      else
        div(
          h3(s"$pubType Published by $compName"),
          table(
            attr("data-bs-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description"),
                if (clientApi) th("Subscribers") else span
              )
            ),
            tbody(
              for (t <- imageList) yield {
                val idStr      = idFor(compName, "publishes", pubType, component.subsystem, compName, t.imageModel.name)
                val (btn, row) = hiddenRowMarkup(idStr, makeImageDetailsRow(t, Some(idStr)), 3)
                List(
                  tr(
                    td(
                      cls := "attributeCell",
                      p(
                        btn,
                        a(id := idStr, name := idStr)(
                          t.imageModel.name
                        )
                      )
                    ),
                    td(raw(t.imageModel.description)),
                    if (clientApi)
                      td(p(t.subscribers.map(_.componentModel).distinct.map(makeLinkForComponent)))
                    else span
                  ),
                  row
                )
              }
            )
          )
        )
    }

    // Returns a div displaying more details for the given alarm
    def makeAlarmDetailsRow(m: AlarmModel) = {
      val headings = List("Severity Levels", "Location", "Alarm Type", "Auto Ack", "Latched")
      val rowList = List(
        List(m.severityLevels.mkString(", "), m.location, m.alarmType, yesNo(m.autoAck), yesNo(m.latched))
      )

      div(
        if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
        if (m.probableCause.isEmpty) div() else p(strong("Probable Cause: "), raw(m.probableCause)),
        if (m.operatorResponse.isEmpty) div() else p(strong("Operator Response: "), raw(m.operatorResponse)),
        mkTable(headings, rowList)
      )
    }

    // Returns the markup for the published alarms
    def publishAlarmListMarkup(alarmList: List[AlarmModel]) = {
      if (alarmList.isEmpty) div()
      else
        div(
          h3(s"Alarms Published by $compName"),
          table(
            attr("data-bs-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for (m <- alarmList) yield {
                val idStr      = idFor(compName, "publishes", "Alarms", component.subsystem, compName, m.name)
                val (btn, row) = hiddenRowMarkup(idStr, makeAlarmDetailsRow(m), 3)
                List(
                  tr(
                    td(
                      cls := "attributeCell",
                      p(btn, a(id := idStr, name := idStr)(m.name))
                    ),
                    td(raw(m.description))
                  ),
                  row
                )
              }
            )
          )
        )
    }

    def totalArchiveSpace(): TypedTag[Element] = {
      val totalYearlyArchiveSpace = {
        val eventList = maybePublishes.toList.flatMap(p => (p.eventList ++ p.observeEventList).map(_.eventModel))
        EventModel.getTotalArchiveSpace(eventList)
      }
      if (totalYearlyArchiveSpace.nonEmpty)
        strong(
          p(
            s"Total yearly space required for archiving events published by ${component.subsystem}.$compName: $totalYearlyArchiveSpace"
          )
        )
      else span()
    }

    maybePublishes match {
      case None => div()
      case Some(publishes) =>
        if (publishes.nonEmpty) {
          div(
            cls := "componentSection",
            raw(publishes.description),
            publishEventListMarkup("Events", publishes.eventList),
            publishEventListMarkup("Observe Events", publishes.observeEventList),
            if (forApi) totalArchiveSpace() else span(),
            publishEventListMarkup("Current States", publishes.currentStateList),
            publishImageListMarkup("Images", publishes.imageList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        }
        else div()
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(component: ComponentModel, maybeSubscribes: Option[Subscribes], forApi: Boolean) = {
    import scalatags.JsDom.all.*

    val compName = component.component

    // Returns a div displaying more details for the given subscription
    def makeDetailsRow(si: DetailedSubscribeInfo) = {
      val sInfo = si.subscribeModelInfo

      def getImageDetailsTable = {
        // Layout for image subscriber details taken from image publisher
        val imageModel = si.imageModel.get
        val maxRate    = imageModel.maybeMaxRate
        val headings =
          List("Subsystem", "Component", "Prefix.Name", "Channel", "Format", "Size", "Pixel Size", "Max Rate")
        val rowList = List(
          List(
            sInfo.subsystem,
            sInfo.component,
            si.path,
            imageModel.channel,
            imageModel.format,
            s"${imageModel.size._1} x ${imageModel.size._1}",
            imageModel.pixelSize.toString,
            formatRate(maxRate)
          )
        )
        val imageMetadataTable = si.imageModel.map(t => imageMetadataListMarkup(t.name, t.metadataList)).getOrElse(div())
        div(
          mkTable(headings, rowList),
          imageMetadataTable
        )
      }

      def getEventDetailsTable = {
        // Layout for event subscriber details taken from event publisher
        val maxRate = si.eventModel.flatMap(_.maybeMaxRate)
        val headings =
          List("Subsystem", "Component", "Prefix.Name", "Max Rate", "Publisher's Max Rate")
        val rowList = List(
          List(
            sInfo.subsystem,
            sInfo.component,
            si.path,
            formatRate(sInfo.maxRate),
            formatRate(maxRate)
          )
        )
        val attrTable = si.eventModel.map(t => eventParameterListMarkup(t.parameterList, forApi)).getOrElse(div())
        div(
          mkTable(headings, rowList),
          if (maxRate.isEmpty) span("* Default maxRate of 1 Hz assumed.") else span(),
          attrTable
        )
      }

      if (si.imageModel.nonEmpty) getImageDetailsTable
      else if (si.eventModel.nonEmpty) getEventDetailsTable
      else div()
    }

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]) = {
      // Warn if no publisher found for subscribed item
      def getWarning(info: DetailedSubscribeInfo) =
        info.warning.map(msg => makeErrorDiv(s" Warning: $msg"))

      if (subscribeList.isEmpty) div()
      else
        div(
          h3(s"$pubType Subscribed to by $compName"),
          div(
            cls := "componentSection",
            table(
              cls                    := "componentTable",
              attr("data-bs-toggle") := "table",
              thead(
                tr(
                  th("Name"),
                  th("Description"),
                  th("Publisher")
                )
              ),
              tbody(
                for (s <- subscribeList) yield {
                  val idStr = idFor(
                    compName,
                    "subscribes",
                    pubType,
                    s.subscribeModelInfo.subsystem,
                    s.subscribeModelInfo.component,
                    s.subscribeModelInfo.name
                  )
                  val (btn, row) = hiddenRowMarkup(idStr, makeDetailsRow(s), 3)
                  val usage =
                    if (s.subscribeModelInfo.usage.isEmpty) div()
                    else
                      div(
                        strong("Usage:"),
                        raw(s.subscribeModelInfo.usage)
                      )
                  List(
                    tr(
                      td(
                        cls := "attributeCell",
                        p(
                          btn,
                          a(id := idStr, name := idStr)(s.subscribeModelInfo.name)
                        )
                      ),
                      td(raw(s.description), getWarning(s), usage),
                      td(p(makeLinkForComponent(s.subscribeModelInfo.subsystem, s.subscribeModelInfo.component)))
                    ),
                    row
                  )
                }
              )
            )
          )
        )
    }

    maybeSubscribes match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            cls := "componentSection",
            raw(subscribes.description),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == Events)),
            subscribeListMarkup("Observe Events", subscribes.subscribeInfo.filter(_.itemType == ObserveEvents)),
            subscribeListMarkup("Current States", subscribes.subscribeInfo.filter(_.itemType == CurrentStates)),
            subscribeListMarkup("Images", subscribes.subscribeInfo.filter(_.itemType == Images)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
          )
        }
        else div()
    }
  }

  // Returns a div displaying more details for the given command
  private def makeReceivedCommandDetailsRow(m: ReceiveCommandModel) = {
    import scalatags.JsDom.all.*
    div(
      if (m.refError.startsWith("Error:")) makeErrorDiv(m.refError) else div(),
      if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
      if (m.preconditions.isEmpty) div() else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
      if (m.postconditions.isEmpty) div() else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
      commandParameterListMarkup(m.parameters, m.requiredArgs),
      p(strong("Completion Type: "), m.completionType),
      resultMarkup(m.maybeResult),
      if (m.completionConditions.isEmpty) div()
      else div(p(strong("Completion Conditions: "), ol(m.completionConditions.map(cc => li(raw(cc)))))),
      if (m.role.isEmpty) div()
      else div(p(strong("Required User Role: "), m.role.get))
    )
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(component: ComponentModel, info: List[ReceivedCommandInfo], clientApi: Boolean) = {
    import scalatags.JsDom.all.*

    // Only display non-empty tables
    if (info.isEmpty) div()
    else {
      val compName = component.component
      div(
        cls := "componentSection",
        h4(s"Command Configurations Received by $compName"),
        table(
          cls                    := "componentTable",
          attr("data-bs-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              if (clientApi) th("Senders") else span
            )
          ),
          tbody(
            for (r <- info) yield {
              val rc         = r.receiveCommandModel
              val idStr      = idFor(compName, "receives", "Commands", component.subsystem, compName, rc.name)
              val (btn, row) = hiddenRowMarkup(idStr, makeReceivedCommandDetailsRow(r.receiveCommandModel), 3)
              List(
                tr(
                  td(
                    cls := "attributeCell",
                    p(
                      btn,
                      a(id := idStr, name := idStr)(rc.name)
                    )
                  ),
                  td(raw(rc.description)),
                  if (clientApi) td(p(r.senders.distinct.map(makeLinkForComponent))) else span
                ),
                row
              )
            }
          )
        )
      )
    }
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(component: ComponentModel, info: List[SentCommandInfo]) = {
    import scalatags.JsDom.all.*

    val compName = component.component

    // Warn if no receiver found for sent command
    def getWarning(m: SentCommandInfo) =
      m.warning.map { msg =>
        div(cls := "alert alert-warning", role := "alert")(
          span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
          span(em(s" Warning: $msg"))
        )
      }

    // Returns the layout for an item describing a sent command
    def makeItem(s: SentCommandInfo) = {
      val idStr = idFor(compName, "sends", "Commands", s.subsystem, s.component, s.name)
      s.receiveCommandModel match {
        case Some(r) =>
          val (btn, row) = hiddenRowMarkup(idStr, makeReceivedCommandDetailsRow(r), 3)
          List(
            tr(
              td(
                cls := "attributeCell",
                p(btn, a(id := idStr, name := idStr)(s.name))
              ),
              td(raw(r.description)),
              td(p(s.receiver.map(makeLinkForComponent)))
            ),
            row
          )
        case None =>
          List(
            tr(
              td(cls := "attributeCell", p(s.name)),
              td(getWarning(s)),
              td(p(s.receiver.map(makeLinkForComponent)))
            )
          )
      }
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else
      div(
        cls := "componentSection",
        h4(s"Command Configurations Sent by $compName"),
        table(
          cls                    := "componentTable",
          attr("data-bs-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Receiver")
            )
          ),
          tbody(
            for (s <- info) yield makeItem(s)
          )
        )
      )
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(component: ComponentModel, maybeCommands: Option[Commands], clientApi: Boolean) = {
    import scalatags.JsDom.all.*
    val compName = component.component
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.isEmpty && commands.commandsSent.isEmpty) div()
        else
          div(
            h3(s"Commands for $compName"),
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, clientApi),
            if (clientApi) sentCommandsMarkup(component, commands.commandsSent) else span
          )
    }
  }

  // Generates the markup for the services section (description plus provides and requires)
  private def servicesMarkup(
      component: ComponentModel,
      maybeServices: Option[Services],
      clientApi: Boolean
  ) = {
    import scalatags.JsDom.all.*
    maybeServices match {
      case None => div()
      case Some(services) =>
        if (services.servicesProvided.nonEmpty || (services.servicesRequired.nonEmpty && clientApi)) {
          div(
            h3(s"Services for ${component.component}"),
            raw(services.description),
            servicesProvidedMarkup(component, services.servicesProvided, clientApi),
            if (clientApi) servicesRequiredMarkup(component, services.servicesRequired) else div()
          )
        }
        else div()
    }
  }

  // Generates the HTML markup to display the HTTP services a component requires
  private def servicesRequiredMarkup(
      component: ComponentModel,
      info: List[ServicesRequiredInfo]
  ) = {
    import scalatags.JsDom.all.*

    val compName = component.component
    if (info.isEmpty) div()
    else {
      div(
        cls := "componentSection",
        h4(s"HTTP Services required by $compName"),
        table(
          cls                    := "componentTable",
          attr("data-bs-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Provider")
            )
          ),
          tbody(
            for (s <- info) yield {
              // Need to serve OpenAPI file
              val openApiUrl = ClientRoutes.openApi(
                s.serviceModelClient.subsystem,
                s.serviceModelClient.component,
                s.serviceModelClient.name,
                s.provider.flatMap(_.maybeSubsystemVersion),
                s.serviceModelClient.paths
              )
              val url = s"assets/openapi/index.html?url=$openApiUrl"
              val openInNewTab = () => {
                dom.window.open(url)
              }
              val idStr = idFor(
                compName,
                "requires",
                "Service",
                s.serviceModelClient.subsystem,
                s.serviceModelClient.component,
                s.serviceModelClient.name
              )
              val m          = s.serviceModelClient
              val (btn, row) = hiddenRowMarkup(idStr, makeProvidedServiceDetailsRow(url), 3)
              val desc =
                s.maybeServiceModelProvider.map(_.description).getOrElse(s"OpenApi description of HTTP Service: ${m.name}")
              List(
                tr(
                  td(
                    cls := "attributeCell",
                    p(
                      btn,
                      a(id := idStr, name := idStr, onclick := openInNewTab, title := s"Open ${m.name} API in new tab.")(m.name)
                    )
                  ),
                  td(p(desc)),
                  td(
                    s.provider
                      .map(makeLinkForComponent)
                      .getOrElse(makeLinkForComponent(s.serviceModelClient.subsystem, s.serviceModelClient.component))
                  )
                ),
                row
              )
            }
          )
        )
      )
    }
  }

  // Returns a div displaying the swagger-ui HTML for the given OpenAPI service.
  // The given url serves the OpenApi file from the play server to the embedded swagger-ui web app.
  private def makeProvidedServiceDetailsRow(url: String) = {
    import scalatags.JsDom.all.*
    div(id := "swagger-ui")(
      embed(
        `type` := "text/html",
        src    := url,
        width  := "100%",
        height := "800px"
      )
    )
  }

  // Generates the HTML markup to display the HTTP services a component provides
  private def servicesProvidedMarkup(
      component: ComponentModel,
      info: List[ServiceProvidedInfo],
      clientApi: Boolean
  ) = {
    import scalatags.JsDom.all.*

    val compName = component.component
    if (info.isEmpty) div()
    else {
      div(
        cls := "componentSection",
        h4(s"HTTP Services provided by $compName"),
        table(
          cls                    := "componentTable",
          attr("data-bs-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              if (clientApi) th("Users") else span
            )
          ),
          tbody(
            for (s <- info) yield {
              val paths = s.requiredBy.flatMap(_.paths).distinct
              // Need to serve OpenAPI file
              val openApiUrl = ClientRoutes.openApi(
                component.subsystem,
                component.component,
                s.serviceModelProvider.name,
                component.maybeSubsystemVersion,
                paths
              )
              val url = s"assets/openapi/index.html?url=$openApiUrl"
              val openInNewTab = () => {
                dom.window.open(url)
              }
              val idStr      = idFor(compName, "provides", "Service", component.subsystem, compName, s.serviceModelProvider.name)
              val m          = s.serviceModelProvider
              val (btn, row) = hiddenRowMarkup(idStr, makeProvidedServiceDetailsRow(url), 3)
              List(
                tr(
                  td(
                    cls := "attributeCell",
                    p(
                      btn,
                      a(id := idStr, name := idStr, onclick := openInNewTab, title := s"Open ${m.name} API in new tab.")(m.name)
                    )
                  ),
                  td(
                    width := "90%",
                    m.description
                  ),
                  if (clientApi) td(s.requiredBy.map(_.component).distinct.map(makeLinkForComponent)) else span
                ),
                row
              )
            }
          )
        )
      )
    }
  }

  // Generates a one line table with basic component information
  private def componentInfoTableMarkup(info: ComponentInfo) = {
    import scalatags.JsDom.all.*

    div(
      table(
        cls                    := "componentTable",
        attr("data-bs-toggle") := "table",
        thead(
          tr(
            th("Subsystem"),
            th("Name"),
            th("Prefix"),
            th("Type"),
            th("WBS ID")
          )
        ),
        tbody(
          tr(
            td(info.componentModel.subsystem),
            td(info.componentModel.component),
            td(info.componentModel.prefix),
            td(info.componentModel.componentType),
            td(info.componentModel.wbsId)
          )
        )
      )
    )
  }

  // Generates table with related FITS key information
  private def makeFitsKeyTable(fitsDict: FitsDictionary, sv: SubsystemWithVersion) = {
    import scalatags.JsDom.all.*

    val fitsKeys = fitsDict.fitsKeys
    div(cls := "component container-fluid", id := "FITS-Keys")(
      h3(a(name := "FITS-Keys")("FITS Keywords")),
      table(
        cls                    := "componentTable",
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
            // If a subsystem and optional component are given, restrict channels to those
            val channels = fitsKey.channels
              .filter(c =>
                sv.subsystem == c.source.subsystem && (sv.maybeComponent.isEmpty ||
                  sv.maybeComponent.contains(c.source.componentName))
              )
            val iList = channels.indices.toList
            val zList = channels.zip(iList)
            tr(
              td(a(id := fitsKey.name, name := fitsKey.name)(fitsKey.name)),
              td(raw(fitsKey.description)),
              td(fitsKey.`type`),
              td(fitsKey.units),
              td(zList.map(p => makeLinkForFitsKeySource(p._1, p._2)))
            )
          }
        )
      )
    )
  }

  // Generates the markup for the Missing Items section
  private def missingItemsMarkup(): TypedTag[Div] = {
    import scalatags.JsDom.all.*
    div()
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(
      info: ComponentInfo,
      forApi: Boolean,
      clientApi: Boolean
  ): TypedTag[Div] = {
    import scalatags.JsDom.all.*

    val idStr = getComponentInfoId(info.componentModel.component)

    div(cls := "component container-fluid", id := idStr)(
      h2(info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel, info.publishes, forApi, clientApi),
      if (clientApi) subscribeMarkup(info.componentModel, info.subscribes, forApi) else span,
      commandsMarkup(info.componentModel, info.commands, clientApi),
      servicesMarkup(info.componentModel, info.services, clientApi)
    )
  }

}
