package csw.services.icd.db

import java.io.File

import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}

/**
  * Command line options ("icd-db --help" prints a usage message with descriptions of all the options)
  */
case class IcdDbOptions(dbName: String = defaultDbName,
                        host: String = defaultHost,
                        port: Int = defaultPort,
                        ingest: Option[File] = None,
                        list: Option[String] = None,
                        subsystem: Option[String] = None,
                        target: Option[String] = None,
                        targetComponent: Option[String] = None,
                        icdVersion: Option[String] = None,
                        component: Option[String] = None,
                        outputFile: Option[File] = None,
                        drop: Option[String] = None,
                        versions: Option[String] = None,
                        diff: Option[String] = None,
                        missing: Option[File] = None,
                        archived: Option[File] = None,
                        listData: Option[String] = None)
