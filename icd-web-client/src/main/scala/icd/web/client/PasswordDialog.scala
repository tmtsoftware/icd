package icd.web.client

import icd.web.client.PasswordDialog.PasswordDialogListener
import icd.web.shared.SharedUtils.Credentials
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.ext.{Ajax, AjaxException}
import play.api.libs.json.Json

import scala.util.Failure
import scalatags.JsDom.all._
import scalacss.ScalatagsCss._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object PasswordDialog {

  /**
   * Type of a listener for actions in the Password dialog
   */
  trait PasswordDialogListener {
    def authenticated(token: String): Unit
  }

}

/**
 * Displays the current published status of a selected subsystem.
 * @param mainContent used to display errors
 */
case class PasswordDialog(mainContent: MainContent, listener: PasswordDialogListener) extends Displayable {
  import icd.web.shared.JsonSupport._

  // Publish user name field
  private val usernameBox = {
    input(
      cls := "form-control",
      name := "username",
      id := "username",
      required,
      onkeyup := usernameChanged _,
      placeholder := "Enter the user name..."
    ).render
  }

  // Message about missing username
  private val usernameMissing = {
    div(id := "usernameMissing", cls := "has-error", label(cls := "control-label", "Username is required!")).render
  }

  private def usernameChanged(): Unit = {
    val username = usernameBox.value
    if (username.isEmpty)
      usernameMissing.classList.remove("hide")
    else
      usernameMissing.classList.add("hide")
  }

  // Publish password field
  private val passwordBox = {
    input(
      cls := "form-control",
      `type` := "password",
      name := "password",
      id := "password",
      onkeyup := passwordChanged _,
      required,
      placeholder := "Enter the password..."
    ).render
  }

  // Message about missing password
  private val passwordMissing = {
    div(id := "passwordMissing", cls := "has-error", label(cls := "control-label", "Password is required!")).render
  }

  // Message about incorrect password
  private val passwordIncorrect = {
    div(id := "passwordIncorrect", cls := "has-error hide", label(cls := "control-label", "Password or username is incorrect!")).render
  }

  private def passwordChanged(): Unit = {
    val password = passwordBox.value
    if (password.isEmpty)
      passwordMissing.classList.remove("hide")
    else
      passwordMissing.classList.add("hide")
    passwordIncorrect.classList.add("hide")
  }

  //noinspection ScalaUnusedSymbol
  private def checkCredentials(e: dom.Event): Unit = {
    val credentials = Credentials(usernameBox.value, passwordBox.value)
    val headers     = Map("Content-Type" -> "application/json")
    val data = Json.toJson(credentials).toString()
    val f =
      Ajax
        .post(url = ClientRoutes.checkCredentials, data = data, headers = headers)
        .map { r =>
          if (r.status == 200) {
            listener.authenticated(data)
          }
          ()
        }
    displayAjaxErrors(f)
    showBusyCursorWhile(f)
  }

  private def displayAjaxErrors(f: Future[Unit]): Unit = {
    f.onComplete {
      case Failure(ex: AjaxException) =>
        ex.xhr.status match {
          case 401 => // Unauthorized
            passwordIncorrect.classList.remove("hide")
        }
      case _ =>
    }
  }

  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    usernameChanged()
    passwordChanged()
    div(
      div(
        id := "Credentials",
        div(Styles.commentBox, label("Username")("*", usernameBox, usernameMissing)),
        div(Styles.commentBox, label("Password")("*", passwordBox, passwordMissing, passwordIncorrect)),
        button(
          `type` := "submit",
          cls := "btn btn-primary",
          id := "applyButton",
          title := s"Login",
          onclick := checkCredentials _
        )("Apply")
      ),
      div(id := "contentDivPlaceholder")
    ).render
  }

}
