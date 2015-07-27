package csw.services.icd.db

import com.mongodb.casbah.Imports._

/**
 * Adds caching to IcdVersionManager for better performance when creating documents or web pages that
 * require access to all subsystems and components.
 *
 * This version only caches the collection names. Alternatively, you can pass a CachedIcdDbQuery instance
 * to cache a lot more information. In tests, the collectionExists method was found to be a bottleneck.
 *
 * @param db the MongoDB handle
 * @param query may be used to share caching of collection names (see CachedIcdDbQuery)
 */
class CachedIcdVersionManager(db: MongoDB, query: IcdDbQuery) extends IcdVersionManager(db, query) {
  private val collectionNames = db.getCollectionNames().toSet

  override def getCollectionNames: Set[String] = collectionNames
  override def collectionExists(name: String): Boolean = collectionNames.contains(name)
}
