package csw.services.icd.db

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import sjsonnet.{DefaultParseCache, SjsonnetMain}

import java.io.{ByteArrayOutputStream, File, InputStream, PrintStream, StringReader}

// Jsonnet preprocessor for icd model files.
object Jsonnet {

  // Create a file from an InputStream
  // https://stackoverflow.com/questions/2782638/is-there-a-nice-safe-quick-way-to-write-an-inputstream-to-a-file-in-scala
  private def inputToFile(is: InputStream, f: File): Unit = {
    val in = scala.io.Source.fromInputStream(is)
    val out = new java.io.PrintWriter(f)
    try { in.getLines().foreach(out.println) }
    finally { out.close() }
  }

  /**
   * Run the given inputStream through jsonnet and return a Config from the result or throw a
   * RuntimeException with the error output.
   * @param inputStream the input jsonnet stream
   * @param fileName the name of the file (may be only symbolic)
   */
  def preprocess(inputStream: InputStream, fileName: String): Config = {
    val stderrStream = new ByteArrayOutputStream()
    val stdoutStream = new ByteArrayOutputStream()
    val stderr       = new PrintStream(stderrStream)
    val stdout       = new PrintStream(stdoutStream)
    val inputFile = File.createTempFile(fileName, "tmp")
    inputToFile(inputStream, inputFile)
    val status = SjsonnetMain.main0(
      Array(inputFile.getPath),
      new DefaultParseCache,
      System.in,
      stdout,
      stderr,
      os.pwd, // working directory
      None
    )
    try {
      if (status != 0)
        throw new RuntimeException(s"$fileName: ${stderrStream.toString("UTF8")}")
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
      inputFile.delete()
    }
  }
}
