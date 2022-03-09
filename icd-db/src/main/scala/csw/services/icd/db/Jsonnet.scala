package csw.services.icd.db

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import sjsonnet.{DefaultParseCache, SjsonnetMain}

import java.io.{ByteArrayOutputStream, File, InputStream, PrintStream, StringReader}

// Jsonnet preprocessor for icd model files.
object Jsonnet {

  /**
   * Run the given file through jsonnet and return a Config from the result or throw a
   * RuntimeException with the error output.
   */
  def preprocess(inputFile: File): Config = {
    val stderrStream = new ByteArrayOutputStream()
    val stdoutStream = new ByteArrayOutputStream()
    val stderr       = new PrintStream(stderrStream)
    val stdout       = new PrintStream(stdoutStream)
    val status = SjsonnetMain.main0(
      Array(inputFile.getName),
      new DefaultParseCache,
      System.in,
      stdout,
      stderr,
      os.Path(inputFile.getParentFile.getAbsolutePath)
    )
    try {
      if (status != 0)
        throw new RuntimeException(s"$inputFile: ${stderrStream.toString("UTF8")}")
      val reader = new StringReader(stdoutStream.toString("UTF8"))
      val config = ConfigFactory.parseReader(reader).resolve(ConfigResolveOptions.noSystem())
      reader.close()
      config
    }
    finally {
      stderr.close()
      stdout.close()
      stderrStream.close()
      stdoutStream.close()
    }
  }
}
