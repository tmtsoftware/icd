package csw.services.icd.db

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import os.Pipe

import java.io.{File, StringReader}

// Jsonnet preprocessor for icd model files.
object Jsonnet {

  /**
   * Run the given file through jsonnet and return a Config from the result or throw a
   * RuntimeException with the error output.
   */
  def preprocess(inputFile: File): Config = {
    val result = os.proc("jsonnet", inputFile.getAbsolutePath).call(check = false, stderr = Pipe)
    if (result.exitCode != 0)
      throw new RuntimeException(s"${result.err.text()}")
    val reader = new StringReader(result.out.text())
    val config = ConfigFactory.parseReader(reader).resolve(ConfigResolveOptions.noSystem())
    reader.close()
    config
  }
}
