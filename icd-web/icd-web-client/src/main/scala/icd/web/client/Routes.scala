package icd.web.client

// XXX TODO: Pass settings from server, see ChatJS.main() for example
object Routes {
  def icdNames = "/icdNames"
  def icdHtml(name: String) = s"/icdHtml/$name"
}
