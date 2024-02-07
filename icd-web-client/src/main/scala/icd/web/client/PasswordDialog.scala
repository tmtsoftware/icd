package icd.web.client

import icd.web.client.PasswordDialog.PasswordDialogListener
import icd.web.shared.SharedUtils.Credentials
import org.scalajs.dom
import org.scalajs.dom.Element
import play.api.libs.json.Json

import scala.util.Failure
import scalatags.JsDom.all.*
import scalacss.ScalatagsCss.*

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

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
  import icd.web.shared.JsonSupport.*

  // Publish user name field
  private val usernameBox = {
    input(
      cls := "form-control",
      name := "icd-username",
      id := "icd-username",
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
      usernameMissing.classList.remove("d-none")
    else
      usernameMissing.classList.add("d-none")
  }

  // Publish password field
  private val passwordBox = {
    input(
      cls := "form-control",
      `type` := "password",
      name := "icd-password",
      id := "icd-password",
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
    div(
      id := "passwordIncorrect",
      cls := "has-error d-none",
      label(cls := "control-label", "Password or username is incorrect!")
    ).render
  }

  private def passwordChanged(): Unit = {
    val password = passwordBox.value
    if (password.isEmpty)
      passwordMissing.classList.remove("d-none")
    else
      passwordMissing.classList.add("d-none")
    passwordIncorrect.classList.add("d-none")
  }

  //noinspection ScalaUnusedSymbol
  private def checkCredentials(e: dom.Event): Unit = {
    val credentials = Credentials(usernameBox.value, passwordBox.value)
    // TODO: encypt before sending
    val data = Json.toJson(credentials).toString()
    val f =
      Fetch
        .post(url = ClientRoutes.checkCredentials, data = data)
        .map { p =>
          if (p._1 == 200) {
            listener.authenticated(data)
          }
          ()
        }
    displayFetchErrors(f)
    showBusyCursorWhile(f)
  }

  private def displayFetchErrors(f: Future[Unit]): Unit = {
    f.onComplete {
      case Failure(ex: Exception) =>
        ex.printStackTrace()
//        ex.xhr.status match {
//          case 401 => // Unauthorized
        passwordIncorrect.classList.remove("d-none")
//        }
      case _ =>
    }
  }

  override def markup(): Element = {
    import scalacss.ScalatagsCss.*
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
