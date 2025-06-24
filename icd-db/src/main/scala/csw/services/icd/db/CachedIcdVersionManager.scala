package csw.services.icd.db

/**
 * Adds caching to IcdVersionManager for better performance when creating documents or web pages that
 * require access to all subsystems and components.
 *
 * This version only caches the collection names. Alternatively, you can pass a CachedIcdDbQuery instance
 * to cache a lot more information. In tests, the collectionExists method was found to be a bottleneck.
 *
 * @param icdDb used to share caching of collection names (see CachedIcdDbQuery)
 */
class CachedIcdVersionManager(icdDb: IcdDb) extends IcdVersionManager(icdDb) {
  private val collectionNames = icdDb.query.getCollectionNames

  override def getCollectionNames: Set[String] = collectionNames

  override def collectionExists(name: String): Boolean = collectionNames.contains(name)
}
