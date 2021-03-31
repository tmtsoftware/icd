package icd.web.shared

object AllEventList {
  // XXX TODO: Add event description?
  case class Event(event: String)
  case class EventsForComponent(component: String, events: List[Event])
  case class EventsForSubsystem(subsystem: String, components: List[EventsForComponent])
}
