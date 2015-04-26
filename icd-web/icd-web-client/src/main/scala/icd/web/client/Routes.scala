package icd.web.client

object Routes {
  def icdNames = "/icdNames"
  def icdHtml(name: String) = s"/icdHtml/$name"
  def upload = "/upload"
}
