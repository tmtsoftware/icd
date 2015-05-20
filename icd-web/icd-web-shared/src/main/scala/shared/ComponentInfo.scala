package shared

/**
 * ICD Component information passed to client
 * @param name component name
 * @param description component description
 * @param publishInfo list of items published by the component
 * @param subscribeInfo list of items the component subscribes to
 * @param commandsReceived list of commands the component can receive
 * @param commandsSent list of commands the component can send
 */
case class ComponentInfo(name: String,
                         description: String,
                         publishInfo: List[PublishInfo],
                         subscribeInfo: List[SubscribeInfo],
                         commandsReceived: List[CommandInfo],
                         commandsSent: List[CommandInfo])

/**
 * Describes a published item
 * @param itemType the type of item ("telemetry", "event", etc.)
 * @param name name of the item
 * @param description description of the item
 * @param subscribers a list of the other components that subscribe to this item
 */
case class PublishInfo(itemType: String,
                       name: String,
                       description: String,
                       subscribers: List[SubscribeInfo])

/**
 * Describes a component that subscribes to an item
 * @param itemType the type of item ("telemetry", "event", etc.)
 * @param name name of the item
 * @param description description of the item
 * @param subsystem the subsystem that publishes the value
 * @param compName name of the component that publishes the value
 */
case class SubscribeInfo(itemType: String,
                         name: String,
                         description: String,
                         subsystem: String,
                         compName: String)

/**
 * Describes another component (receiver, for sent commands, sender for received commands)
 * @param subsystem the subsystem of the other component
 * @param compName the other component
 */
case class OtherComponent(subsystem: String,
                          compName: String)

/**
 * Describes a configuration command sent to or received by a component
 * @param name the name of the command
 * @param description description of the command
 * @param otherComponents the other component (receiver, for sent commands, sender for received commands)
 */
case class CommandInfo(name: String,
                       description: String,
                       otherComponents: List[OtherComponent])
