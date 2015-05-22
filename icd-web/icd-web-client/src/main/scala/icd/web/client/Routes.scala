package icd.web.client

// XXX TODO: Pass settings from server, see ChatJS.main() for example
object Routes {
  val icdNames = "/icdNames"
  def icdComponents(name: String) = s"/icdComponents/$name"
  def componentInfo(name: String) = s"/componentInfo/$name"
  def icdHtml(name: String) = s"/icdHtml/$name"
  def icdPdf(name: String) = s"/icdPdf/$name"
  val uploadFiles = "/uploadFiles"
  def versions(name: String) = s"/versions/$name"
}
