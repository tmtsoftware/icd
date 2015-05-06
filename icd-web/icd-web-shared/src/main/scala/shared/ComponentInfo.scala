package shared

/**
 * ICD Component information passed to client
 * @param name component name
 * @param description component description
 * @param publishInfo list of items published by the component
 */
case class ComponentInfo(name: String, description: String, publishInfo: List[PublishInfo])

/**
 * Describes a published item
 * @param name name of the item
 * @param description description of the item
 * @param itemType the type of item ("telemetry", "event", etc.)
 * @param subscribers a list of the other components that subscribe to this item
 */
case class PublishInfo(itemType: String, name: String, description: String, subscribers: List[SubscribeInfo])

/**
 * Describes a component that subscribes to an item
 * @param subsystem the subsystem that publishes the value
 * @param name the subscriber's component name
 */
case class SubscribeInfo(subsystem: String, name: String)
