package io.mdcatapult.rawtext.extractors

import java.io._
import java.nio.file.Paths

import com.typesafe.config.Config
import io.mdcatapult.source.{Source, SourceReader}
import scala.util.Try

class RawText(source: String)(implicit config: Config) {

  // set the target path using the source parent directory path and the configured `to` path
  lazy val targetPath: String = getTargetPath(source, config.getString("doclib.derivative.target-dir"), Try(config.getString("consumer.name")).toOption)
  lazy val relativeFilePath: String = Paths.get(targetPath, "raw.txt").toString
  private val doclibRoot: String = s"${config.getString("doclib.root").replaceFirst("""/+$""", "")}/"

  def getAbsPath(path: String): String = {
    Paths.get(doclibRoot, path).toAbsolutePath.toString
  }

  /**
   * determines common root paths for two path string
   * @param paths List[String]
   * @return String common path component
   */
  protected def commonPath(paths: List[String]): String = {
    val SEP = "/"
    val BOUNDARY_REGEX = s"(?=[$SEP])(?<=[^$SEP])|(?=[^$SEP])(?<=[$SEP])"
    def common(a: List[String], b: List[String]): List[String] = (a, b) match {
      case (aa :: as, bb :: bs) if aa equals bb => aa :: common(as, bs)
      case _ => Nil
    }
    if (paths.length < 2) paths.headOption.getOrElse("")
    else paths.map(_.split(BOUNDARY_REGEX).toList).reduceLeft(common).mkString
  }

  /**
   * generate new file path maintaining file path from origin but allowing for intersection of common root paths
   * @param source String
   * @return String full path to new target
   */
  def getTargetPath(source: String, base: String, prefix: Option[String] = None): String = {
    val targetRoot = base.replaceAll("/+$", "")
    val regex = """(.*)/(.*)$""".r
    source match {
      case regex(path, file) =>
        val c = commonPath(List(targetRoot, path))
        val targetPath  = scrub(path.replaceAll(s"^$c", "").replaceAll("^/+|/+$", ""))
        Paths.get(config.getString("doclib.local.temp-dir"), targetRoot, targetPath, s"${prefix.getOrElse("")}-$file").toString
      case _ => source
    }
  }

  def scrub(path: String):String  = path match {
    case path if path.startsWith(config.getString("doclib.local.target-dir")) =>
      scrub(path.replaceFirst(s"^${config.getString("doclib.local.target-dir")}/*", ""))
    case path if path.startsWith(config.getString("doclib.derivative.target-dir"))  =>
      scrub(path.replaceFirst(s"^${config.getString("doclib.derivative.target-dir")}/*", ""))
    case _ => path
  }

  /**
    * Extract the source file to a raw text file.
    *
    * @return the new raw text filepath as a String
    */
  def extract : String = {
    val sourcePath = Paths.get(getAbsPath(source))
    val target = new File(getAbsPath(relativeFilePath))
    target.getParentFile.mkdirs()
    val bw = new BufferedWriter(new FileWriter(target))
    val src = Source.fromFile(sourcePath.toFile)
    bw.write(SourceReader().read(src).mkString("\n"))
    bw.close()
    relativeFilePath
  }

}
