package csw.services.icd

/**
 * Describes any validation problems found
 * @param severity a string describing the error severity: fatal, error, warning, etc.
 * @param message describes the problem
 */
case class Problem(severity: String, message: String)

