package icd.web.shared

/**
 * Data returned from the server for a histogram
 */
case class EventsHistogramData(xData: Seq[Double], yData: Seq[Double])

/**
 * Options for creating a histogram based on event data
 * @param histogramType one of "eventSize" (in bytes), "dataRate" (event size * max rate)
 * @param swapAxis if true, swap the X and Y axis
 */
case class EventsHistogramOptions(histogramType: String, swapAxis: Boolean)

object EventsHistogramOptions {
  val eventSize                         = "eventSize"
  val dataRate                          = "dataRate"
  val histogramTypes: Array[String]     = Array(eventSize, dataRate)
  val histogramTypeNames: Array[String] = Array("Event Size (bytes)", "Data Rate (event size * max rate)")
  val defaultHistogramType: String      = histogramTypes.head
  val defaultSwapAxis                   = false
}
