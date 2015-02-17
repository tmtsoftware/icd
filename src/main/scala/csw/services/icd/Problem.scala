package csw.services.icd

/**
 * Describes any validation problems found
 * @param severity a string describing the error severity: fatal, error, warning, etc.
 * @param message describes the problem
 * @param json additional information about the problem in JSON format
 */
case class Problem(severity: String, message: String, json: String = "")

