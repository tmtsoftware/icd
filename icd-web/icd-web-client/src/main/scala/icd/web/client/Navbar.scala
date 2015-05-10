package icd.web.client

import org.scalajs.dom.Element

import scalatags.JsDom.TypedTag

/**
 * Manages the navbar
 */
object Navbar {

  // The HTML id of the outer navbar
  private val navbarId = "icd-navbar"

  // Reference the outer navbar
  private lazy val navbar = $id(navbarId)

  // The HTML id of the left side navbar element
  private val leftNavbarId = "left-navbar"

  // Reference the left side navbar element
  private lazy val leftNavbar = $id(leftNavbarId)

  // The HTML id of the right side navbar element
  private val rightNavbarId = "right-navbar"

  // Reference the right side navbar element
  private lazy val rightNavbar = $id(rightNavbarId)


  //    <ul class="nav navbar-nav pull-right">
  //      <li class="dropdown">
  //        <a id="nbAcctDD" class="dropdown-toggle" data-toggle="dropdown"><i class="icon-user"></i>
  //          Username<i class="icon-sort-down"></i></a>
  //        <ul class="dropdown-menu pull-right">
  //          <li><a >Log Out</a></li>
  //        </ul>
  //      </li>
  //    </ul>

  private def markup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    ul(cls := "nav navbar-nav", id := leftNavbarId)
  }

  private def rightSideMarkup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    ul(cls := "nav navbar-nav pull-right", id := rightNavbarId)
  }


  // Creates the navbar item
  def init(): Unit = {
    navbar.appendChild(markup().render)
    navbar.appendChild(rightSideMarkup().render)
  }

  /**
   * Adds an HTML element to the navbar.
   * @param elem a scalatags element
   */
  def addItem(elem: TypedTag[Element]): Unit = leftNavbar.appendChild(elem.render)

  /**
   * Adds an HTML element to the navbar on the right side.
   * @param elem a scalatags element
   */
  def addRightSideItem(elem: TypedTag[Element]): Unit = leftNavbar.appendChild(elem.render)

}
