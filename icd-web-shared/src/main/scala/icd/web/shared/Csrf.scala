package icd.web.shared

case class Csrf(csrf: String) {
  override def toString = csrf
}
